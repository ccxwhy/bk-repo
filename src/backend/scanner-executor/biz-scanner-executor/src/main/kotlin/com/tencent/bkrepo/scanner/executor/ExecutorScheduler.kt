/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2022 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bkrepo.scanner.executor

import com.sun.management.OperatingSystemMXBean
import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.scanner.pojo.scanner.ScanExecutorResult
import com.tencent.bkrepo.common.scanner.pojo.scanner.SubScanTaskStatus
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.common.storage.credentials.StorageCredentials
import com.tencent.bkrepo.repository.api.StorageCredentialsClient
import com.tencent.bkrepo.analyst.api.ScanClient
import com.tencent.bkrepo.scanner.executor.configuration.ScannerExecutorProperties
import com.tencent.bkrepo.scanner.executor.pojo.ScanExecutorTask
import com.tencent.bkrepo.analyst.pojo.SubScanTask
import com.tencent.bkrepo.analyst.pojo.request.ReportResultRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.stereotype.Component
import java.io.File
import java.lang.management.ManagementFactory
import java.util.concurrent.ConcurrentHashMap

@Component
class ExecutorScheduler @Autowired constructor(
    private val scanExecutorFactory: ScanExecutorFactory,
    private val storageCredentialsClient: StorageCredentialsClient,
    private val scanClient: ScanClient,
    private val storageService: StorageService,
    private val executor: ThreadPoolTaskExecutor,
    private val scannerExecutorProperties: ScannerExecutorProperties
) {

    private val executingSubtaskExecutorMap = ConcurrentHashMap<String, ScanExecutor>()
    private val operatingSystemBean by lazy { ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean }

    @Scheduled(fixedDelay = FIXED_DELAY, initialDelay = FIXED_DELAY)
    fun scan() {
        while (allowExecute()) {
            val subtask = scanClient.pullSubTask().data ?: break
            if (scanning(subtask.taskId)) {
                // 任务执行中，直接忽略新任务
                logger.warn("subtask[${subtask.taskId}] of task[${subtask.parentScanTaskId}] is executing")
                return
            }

            scanClient.updateSubScanTaskStatus(subtask.taskId, SubScanTaskStatus.EXECUTING.name)

            executingSubtaskExecutorMap[subtask.taskId] = scanExecutorFactory.get(subtask.scanner.type)
            logger.info("task start, executing task count ${executingSubtaskExecutorMap.size}")
            executor.execute {
                try {
                    doScan(subtask)
                } finally {
                    executingSubtaskExecutorMap.remove(subtask.taskId)
                    logger.info("task finished, executing task count ${executingSubtaskExecutorMap.size}")
                }
            }
        }
    }

    /**
     * 判断任务是否正在执行
     */
    fun scanning(taskId: String): Boolean {
        return executingSubtaskExecutorMap.containsKey(taskId)
    }

    /**
     * 是否允许执行扫描
     */
    private fun allowExecute(): Boolean {
        val executingCount = executingSubtaskExecutorMap.size

        val freeMem = operatingSystemBean.freePhysicalMemorySize
        val totalMem = operatingSystemBean.totalPhysicalMemorySize
        val freeMemPercent = freeMem.toDouble() / totalMem.toDouble()
        val memAvailable = freeMemPercent > scannerExecutorProperties.atLeastFreeMemPercent

        val workDir = File(scannerExecutorProperties.workDir)
        if (!workDir.exists()) {
            workDir.mkdirs()
        }
        val usableDiskSpacePercent = workDir.usableSpace.toDouble() / workDir.totalSpace
        val diskAvailable = usableDiskSpacePercent > scannerExecutorProperties.atLeastUsableDiskSpacePercent

        if (!memAvailable || !diskAvailable) {
            logger.warn(
                "memory[$freeMemPercent]: $freeMem / $totalMem, " +
                    "disk space[$usableDiskSpacePercent]: $usableDiskSpacePercent / ${workDir.totalSpace}"
            )
        }

        return executingCount < scannerExecutorProperties.maxTaskCount && memAvailable && diskAvailable
    }

    private fun doScan(subScanTask: SubScanTask) {
        with(subScanTask) {
            val startTimestamp = System.currentTimeMillis()

            // 1. 加载文件
            logger.info("start load file[$sha256]")
            // 文件大小超过限制直接返回
            val fileSizeLimit = scannerExecutorProperties.fileSizeLimit.toBytes()
            if (packageSize > fileSizeLimit) {
                logger.warn(
                    "file too large, sha256[$sha256, credentials: [$credentialsKey], subtaskId[$taskId]" +
                        ", size[$packageSize], limit[$fileSizeLimit]"
                )
                report(taskId, parentScanTaskId, startTimestamp)
                return
            }
            val storageCredentials = credentialsKey?.let { storageCredentialsClient.findByKey(it).data!! }
            val artifactInputStream = storageService.load(sha256, Range.full(size), storageCredentials)
            // 加载文件失败，直接返回
            if (artifactInputStream == null) {
                logger.warn("Load storage file failed: sha256[$sha256, credentials: [$credentialsKey]")
                report(taskId, parentScanTaskId, startTimestamp)
                return
            }
            logger.info("load file[$sha256] success, elapse ${System.currentTimeMillis() - startTimestamp}")

            // 2. 执行扫描任务
            val result = try {
                logger.info("start to scan file[$sha256]")
                val executorTask = convert(subScanTask, artifactInputStream, storageCredentials)
                val executor = scanExecutorFactory.get(subScanTask.scanner.type)
                executor.scan(executorTask)
            } catch (e: Exception) {
                logger.error(
                    "scan failed, parentTaskId[$parentScanTaskId], subTaskId[$taskId], " +
                        "sha256[$sha256], scanner[${scanner.name}]]",
                    e
                )
                null
            }

            // 3. 上报扫描结果
            val finishedTimestamp = System.currentTimeMillis()
            val timeSpent = finishedTimestamp - startTimestamp
            logger.info(
                "scan finished[${result?.scanStatus}], timeSpent[$timeSpent], size[$packageSize], " +
                    "subtaskId[$taskId], sha256[$sha256], reporting result"
            )
            report(taskId, parentScanTaskId, startTimestamp, finishedTimestamp, result)
        }
    }

    private fun convert(
        subScanTask: SubScanTask,
        artifactInputStream: ArtifactInputStream,
        storageCredentials: StorageCredentials?
    ): ScanExecutorTask {
        with(subScanTask) {
            return ScanExecutorTask(
                taskId = taskId,
                parentTaskId = parentScanTaskId,
                inputStream = artifactInputStream,
                scanner = scanner,
                projectId = projectId,
                repoName = repoName,
                repoType = repoType,
                fullPath = fullPath,
                sha256 = sha256,
                storageCredentials = storageCredentials
            )
        }
    }

    private fun report(
        subtaskId: String,
        parentTaskId: String,
        startTimestamp: Long,
        finishedTimestamp: Long = System.currentTimeMillis(),
        result: ScanExecutorResult? = null
    ) {
        val request = ReportResultRequest(
            subTaskId = subtaskId,
            parentTaskId = parentTaskId,
            startTimestamp = startTimestamp,
            finishedTimestamp = finishedTimestamp,
            scanStatus = result?.scanStatus ?: SubScanTaskStatus.FAILED.name,
            scanExecutorResult = result
        )
        scanClient.report(request)
    }

    companion object {
        private const val FIXED_DELAY = 3000L
        private val logger = LoggerFactory.getLogger(ExecutorScheduler::class.java)
    }
}
