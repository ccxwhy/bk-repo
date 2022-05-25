/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.bkrepo.job.batch

import com.tencent.bkrepo.common.artifact.event.repo.RepoRefreshedEvent
import com.tencent.bkrepo.common.service.log.LoggerHolder
import com.tencent.bkrepo.common.stream.event.supplier.EventSupplier
import com.tencent.bkrepo.job.CATEGORY
import com.tencent.bkrepo.job.TYPE
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.batch.base.MongoDbBatchJob
import com.tencent.bkrepo.job.config.RepoRefreshJobProperties
import com.tencent.bkrepo.job.exception.JobExecuteException
import java.time.Duration
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.scheduling.annotation.Scheduled

/**
 * 用于remote类型或者composite类型仓库定时从远程代理刷新信息
 */
class RemoteRepoRefreshJob(
    private val properties: RepoRefreshJobProperties,
    private val eventSupplier: EventSupplier
) : MongoDbBatchJob<RemoteRepoRefreshJob.ProxyRepoData>(properties) {

    private val types: List<String>
        get() = properties.types

    private val categories: List<String>
        get() = properties.categories

    @Scheduled(fixedDelay = 3600 * 1000L, initialDelay = 90 * 1000L)
    override fun start(): Boolean {
        return super.start()
    }

    override fun entityClass(): Class<ProxyRepoData> {
        return ProxyRepoData::class.java
    }

    override fun collectionNames(): List<String> {
        return listOf(COLLECTION_NAME)
    }

    override fun buildQuery(): Query {
        return Query(
            Criteria.where(TYPE).`in`(properties.types)
                .and(CATEGORY).`in`(properties.categories)
        )
    }

    override fun run(row: ProxyRepoData, collectionName: String, context: JobContext) {
        with(row) {
            try {
                logger.info("send repo refresh message with $projectId|$name")
                val event = buildRefreshEvent(
                    projectId = projectId,
                    repoName = name,
                    userId = createdBy
                )
                eventSupplier.delegateToSupplier(
                    event = event,
                    topic = event.topic
                )
            } catch (e: Exception) {
                throw JobExecuteException("Failed to send repo refresh event for repo ${row.projectId}|${row.name}.", e)
            }
        }
    }

    override fun getLockAtMostFor(): Duration = Duration.ofDays(7)

    /**
     * 仓库刷新事件
     */
    private fun buildRefreshEvent(
        projectId: String,
        repoName: String,
        userId: String
    ): RepoRefreshedEvent {
        return RepoRefreshedEvent(
            projectId = projectId,
            repoName = repoName,
            userId = userId
        )
    }

    companion object {
        private val logger = LoggerHolder.jobLogger
        private const val COLLECTION_NAME = "repository"
    }

    override fun mapToObject(row: Map<String, Any?>): ProxyRepoData {
        return ProxyRepoData(row)
    }

    data class ProxyRepoData(private val map: Map<String, Any?>) {
        val name: String by map
        val projectId: String by map
        val createdBy: String by map
    }
}
