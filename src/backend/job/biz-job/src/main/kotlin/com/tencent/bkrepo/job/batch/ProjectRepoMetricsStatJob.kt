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

package com.tencent.bkrepo.job.batch

import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.job.CREATED_DATE
import com.tencent.bkrepo.job.DELETED_DATE
import com.tencent.bkrepo.job.PATH
import com.tencent.bkrepo.job.PROJECT
import com.tencent.bkrepo.job.REPO
import com.tencent.bkrepo.job.SHARDING_COUNT
import com.tencent.bkrepo.job.batch.base.DefaultContextMongoDbJob
import com.tencent.bkrepo.job.batch.base.JobContext
import com.tencent.bkrepo.job.batch.context.ProjectRepoMetricsStatJobContext
import com.tencent.bkrepo.job.batch.utils.FolderUtils
import com.tencent.bkrepo.job.batch.utils.MongoShardingUtils
import com.tencent.bkrepo.job.batch.utils.TimeUtils
import com.tencent.bkrepo.job.config.properties.ProjectRepoMetricsStatJobProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.isEqualTo
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 项目仓库指标统计任务
 */
@Component
@EnableConfigurationProperties(ProjectRepoMetricsStatJobProperties::class)
class ProjectRepoMetricsStatJob(
    private val properties: ProjectRepoMetricsStatJobProperties,
    private val mongoTemplate: MongoTemplate
) : DefaultContextMongoDbJob<ProjectRepoMetricsStatJob.Repository>(properties) {
    override fun collectionNames(): List<String> {
        return listOf(COLLECTION_REPOSITORY_NAME)
    }

    override fun buildQuery(): Query = Query()

    override fun mapToEntity(row: Map<String, Any?>): Repository {
        return Repository(row)
    }

    override fun entityClass(): Class<Repository> {
        return Repository::class.java
    }

    override fun run(row: Repository, collectionName: String, context: JobContext) {
        require(context is ProjectRepoMetricsStatJobContext)
        with(row) {
            if (deleted != null) return
            val query = Query(
                Criteria.where(PROJECT).isEqualTo(projectId).and(REPO).isEqualTo(name)
                    .and(PATH).isEqualTo(PathUtils.ROOT).and(DELETED_DATE).isEqualTo(null)
            )
            val nodeCollectionName = COLLECTION_NODE_PREFIX +
                MongoShardingUtils.shardingSequence(projectId, SHARDING_COUNT)
            val nodes = mongoTemplate.find(query, Node::class.java, nodeCollectionName)
            if (nodes.isEmpty()) return
            nodes.forEach {
                val key = FolderUtils.buildCacheKey(collectionName = nodeCollectionName, projectId = projectId)
                val metric = context.metrics.getOrPut(key) {
                    ProjectRepoMetricsStatJobContext.ProjectMetrics(projectId)
                }
                if (!it.folder) {
                    metric.nodeNum.increment()
                } else {
                    val nodeNum = it.nodeNum ?: 0
                    metric.nodeNum.add(nodeNum)
                }
                metric.capSize.add(it.size)
                metric.nodeNum.increment()
                metric.addRepoMetrics(row = it, credentialsKey = credentialsKey, repoType = type)
            }
        }
    }

    override fun getLockAtMostFor(): Duration {
        return Duration.ofDays(7)
    }

    override fun onRunCollectionFinished(collectionName: String, context: JobContext) {
        super.onRunCollectionFinished(collectionName, context)
        require(context is ProjectRepoMetricsStatJobContext)
        for (entry in context.metrics) {
            storeMetrics(context.statDate, entry.value)
        }
        context.metrics.clear()
    }

    override fun createJobContext(): ProjectRepoMetricsStatJobContext{
        return ProjectRepoMetricsStatJobContext(statDate = LocalDate.now().atStartOfDay())
    }



    private fun storeMetrics(
        statDate: LocalDateTime,
        projectMetric: ProjectRepoMetricsStatJobContext.ProjectMetrics
    ) {
        val projectId = projectMetric.projectId
        // insert project repo metrics
        var criteria = Criteria.where(PROJECT).isEqualTo(projectId).and(CREATED_DATE).isEqualTo(statDate)

        mongoTemplate.remove(Query(criteria), COLLECTION_NAME_PROJECT_METRICS)
        logger.info("start to insert project's metrics ")
        mongoTemplate.insert(projectMetric.toDO(statDate), COLLECTION_NAME_PROJECT_METRICS)
        logger.info("stat project metrics done")
    }

    data class Repository(
        var projectId: String,
        var name: String,
        var type: String,
        var credentialsKey: String = "default",
        val deleted: LocalDateTime? = null
    ) {
        constructor(map: Map<String, Any?>) : this(
            map[Repository::projectId.name].toString(),
            map[Repository::name.name].toString(),
            map[Repository::type.name].toString(),
            map[Repository::credentialsKey.name]?.toString() ?: "default",
            map[Repository::deleted.name]?.let { TimeUtils.parseMongoDateTimeStr(it.toString()) }
        )
    }

    data class Node(
        val id: String,
        val projectId: String,
        val repoName: String,
        val path: String,
        val fullPath: String,
        val folder: Boolean,
        val size: Long,
        val nodeNum: Long? = null,
        val createdDate: LocalDateTime,
        val deleted: LocalDateTime? = null
    ) {
        constructor(map: Map<String, Any?>) : this(
            map[Node::id.name].toString(), map[Node::projectId.name].toString(),
            map[Node::repoName.name].toString(), map[Node::path.name].toString(),
            map[Node::fullPath.name].toString(), map[Node::folder.name] as Boolean,
            map[Node::size.name].toString().toLong(),
            map[Node::nodeNum.name]?.toString()?.toLong(),
            TimeUtils.parseMongoDateTimeStr(map[Node::createdDate.name].toString())!!,
            map[Node::deleted.name]?.let { TimeUtils.parseMongoDateTimeStr(it.toString()) }
        )
    }


    data class TProjectMetrics(
        var projectId: String,
        var nodeNum: Long,
        var capSize: Long,
        val repoMetrics: List<TRepoMetrics>,
        val createdDate: LocalDateTime? = LocalDateTime.now()
    )

    data class TRepoMetrics(
        val repoName: String,
        val credentialsKey: String? = "default",
        val size: Long,
        val num: Long,
        val type: String,
    )

    companion object {
        private val logger = LoggerFactory.getLogger(ProjectRepoMetricsStatJob::class.java)
        private const val COLLECTION_REPOSITORY_NAME = "repository"
        private const val COLLECTION_NODE_PREFIX = "node_"
        private const val COLLECTION_NAME_PROJECT_METRICS = "project_metrics"
    }
}
