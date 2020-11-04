package com.tencent.bkrepo.helm.service.impl

import com.tencent.bkrepo.auth.pojo.enums.PermissionAction
import com.tencent.bkrepo.auth.pojo.enums.ResourceType
import com.tencent.bkrepo.common.api.constant.CharPool
import com.tencent.bkrepo.common.api.util.readYamlString
import com.tencent.bkrepo.common.artifact.path.PathUtils
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactContextHolder
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactQueryContext
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactChannel
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.artifact.stream.ArtifactInputStream
import com.tencent.bkrepo.common.security.permission.Permission
import com.tencent.bkrepo.helm.artifact.HelmArtifactInfo
import com.tencent.bkrepo.helm.constants.CHART_PACKAGE_FILE_EXTENSION
import com.tencent.bkrepo.helm.constants.DATA_TIME_FORMATTER
import com.tencent.bkrepo.helm.constants.FULL_PATH
import com.tencent.bkrepo.helm.constants.INDEX_CACHE_YAML
import com.tencent.bkrepo.helm.constants.NODE_CREATE_DATE
import com.tencent.bkrepo.helm.constants.NODE_FULL_PATH
import com.tencent.bkrepo.helm.constants.NODE_NAME
import com.tencent.bkrepo.helm.constants.NODE_SHA256
import com.tencent.bkrepo.helm.exception.HelmFileNotFoundException
import com.tencent.bkrepo.helm.model.metadata.HelmChartMetadata
import com.tencent.bkrepo.helm.model.metadata.HelmIndexYamlMetadata
import com.tencent.bkrepo.helm.service.ChartRepositoryService
import com.tencent.bkrepo.helm.utils.DecompressUtil.getArchivesContent
import com.tencent.bkrepo.helm.utils.HelmUtils
import com.tencent.bkrepo.helm.utils.HelmZipResponseWriter
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class ChartRepositoryServiceImpl : AbstractService(), ChartRepositoryService {

    @Value("\${helm.registry.domain: ''}")
    private lateinit var domain: String

    @Permission(ResourceType.REPO, PermissionAction.READ)
    override fun queryIndexYaml(artifactInfo: HelmArtifactInfo) {
        // val lockKey = "${artifactInfo.projectId}_${artifactInfo.repoName}"
        // try {
        //     if (mongoLock.tryLock(lockKey, LOCK_VALUE)) {
        //         freshIndexFile(artifactInfo)
        //     }
        // } finally {
        //     mongoLock.releaseLock(lockKey, LOCK_VALUE)
        // }
        freshIndexFile(artifactInfo)
        downloadIndexYaml()
    }

    @Synchronized
    override fun freshIndexFile(artifactInfo: HelmArtifactInfo) {
        // 先查询index.yaml文件，如果不存在则创建，
        // 存在则根据最后一次更新时间与node节点创建时间对比进行增量更新
        if (!exist(artifactInfo.projectId, artifactInfo.repoName, INDEX_CACHE_YAML)) {
            val nodeList = queryNodeList(artifactInfo, false)
            logger.info("query node list success, size [${nodeList.size}], start generate index.yaml ... ")
            val indexYamlMetadata = buildIndexYamlMetadata(nodeList, artifactInfo)
            uploadIndexYamlMetadata(indexYamlMetadata).also { logger.info("generate index.yaml success！") }
            return
        }

        val originalYamlMetadata = getOriginalIndexYaml()
        val dateTime = originalYamlMetadata.generated.let { LocalDateTime.parse(it, DateTimeFormatter.ISO_DATE_TIME) }
        val now = LocalDateTime.now()
        val nodeList = queryNodeList(artifactInfo, lastModifyTime = dateTime)
        if (nodeList.isNotEmpty()) {
            val indexYamlMetadata = buildIndexYamlMetadata(nodeList, artifactInfo)
            logger.info(
                "start regenerate index.yaml, original index.yaml entries size : [${indexYamlMetadata.entriesSize()}]"
            )
            indexYamlMetadata.generated = now.format(DateTimeFormatter.ofPattern(DATA_TIME_FORMATTER))
            uploadIndexYamlMetadata(indexYamlMetadata).also {
                logger.info(
                    "regenerate index.yaml success, current index.yaml entries size : [${indexYamlMetadata.entriesSize()}]"
                )
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun buildIndexYamlMetadata(
        result: List<Map<String, Any?>>,
        artifactInfo: HelmArtifactInfo,
        isInit: Boolean
    ): HelmIndexYamlMetadata {
        with(artifactInfo) {
            val indexYamlMetadata = if (!exist(projectId, repoName, HelmUtils.getIndexYamlFullPath()) || isInit) {
                HelmUtils.initIndexYamlMetadata()
            } else {
                getOriginalIndexYaml()
            }
            if (result.isNotEmpty()) {
                val context = ArtifactQueryContext()
                result.forEach { it ->
                    Thread.sleep(SLEEP_MILLIS)
                    context.putAttribute(FULL_PATH, it[NODE_FULL_PATH] as String)
                    var chartName: String? = null
                    var chartVersion: String? = null
                    try {
                        val artifactInputStream =
                            ArtifactContextHolder.getRepository().query(context) as ArtifactInputStream
                        val content = artifactInputStream.use { it.getArchivesContent(CHART_PACKAGE_FILE_EXTENSION) }
                        val chartMetadata = content.byteInputStream().readYamlString<HelmChartMetadata>()
                        chartName = chartMetadata.name
                        chartVersion = chartMetadata.version
                        chartMetadata.urls = listOf(
                            domain.trimEnd(CharPool.SLASH) + PathUtils.normalizeFullPath(
                                "$projectId/$repoName/charts/$chartName-$chartVersion.tgz"
                            )
                        )
                        chartMetadata.created = convertDateTime(it[NODE_CREATE_DATE] as String)
                        chartMetadata.digest = it[NODE_SHA256] as String
                        addIndexEntries(indexYamlMetadata, chartMetadata)
                    } catch (ex: HelmFileNotFoundException) {
                        logger.error(
                            "generate indexFile for chart [$chartName-$chartVersion.tgz] in " +
                                "[${artifactInfo.projectId}/${artifactInfo.repoName}] failed, ${ex.message}"
                        )
                    }
                }
            }
        return indexYamlMetadata
        }
    }

    fun addIndexEntries(
        indexYamlMetadata: HelmIndexYamlMetadata,
        chartMetadata: HelmChartMetadata
    ) {
        val chartName = chartMetadata.name
        val chartVersion = chartMetadata.version
        val isFirstChart = !indexYamlMetadata.entries.containsKey(chartMetadata.name)
        indexYamlMetadata.entries.let {
            if (isFirstChart) {
                it[chartMetadata.name] = sortedSetOf(chartMetadata)
            } else {
                // force upload
                run stop@{
                    it[chartName]?.forEachIndexed { _, helmChartMetadata ->
                        if (chartVersion == helmChartMetadata.version) {
                            it[chartName]?.remove(helmChartMetadata)
                            return@stop
                        }
                    }
                }
                it[chartName]?.add(chartMetadata)
            }
        }
    }

    fun downloadIndexYaml() {
        val context = ArtifactDownloadContext()
        context.putAttribute(FULL_PATH, HelmUtils.getIndexYamlFullPath())
        ArtifactContextHolder.getRepository().download(context)
    }

    @Permission(ResourceType.REPO, PermissionAction.READ)
    @Transactional(rollbackFor = [Throwable::class])
    override fun installTgz(artifactInfo: HelmArtifactInfo) {
        val context = ArtifactDownloadContext()
        context.putAttribute(FULL_PATH, artifactInfo.getArtifactFullPath())
        ArtifactContextHolder.getRepository().download(context)
    }

    @Permission(ResourceType.REPO, PermissionAction.READ)
    @Transactional(rollbackFor = [Throwable::class])
    override fun installProv(artifactInfo: HelmArtifactInfo) {
        val context = ArtifactDownloadContext()
        context.putAttribute(FULL_PATH, artifactInfo.getArtifactFullPath())
        ArtifactContextHolder.getRepository().download(context)
    }

    @Permission(ResourceType.REPO, PermissionAction.READ)
    @Transactional(rollbackFor = [Throwable::class])
    override fun regenerateIndexYaml(artifactInfo: HelmArtifactInfo) {
        val nodeList = queryNodeList(artifactInfo, false)
        logger.info("query node list for full refresh index.yaml success, size [${nodeList.size}], starting full refresh index.yaml ... ")
        val indexYamlMetadata = buildIndexYamlMetadata(nodeList, artifactInfo)
        uploadIndexYamlMetadata(indexYamlMetadata).also { logger.info("Full refresh index.yaml success！") }
    }

    @Permission(ResourceType.REPO, PermissionAction.READ)
    @Transactional(rollbackFor = [Throwable::class])
    override fun batchInstallTgz(artifactInfo: HelmArtifactInfo, startTime: LocalDateTime) {
        val artifactResourceList = mutableListOf<ArtifactResource>()
        val nodeList = queryNodeList(artifactInfo, lastModifyTime = startTime)
        if (nodeList.isEmpty()) {
            throw HelmFileNotFoundException(
                "no chart found in repository [${artifactInfo.projectId}/${artifactInfo.repoName}]"
            )
        }
        val context = ArtifactQueryContext()
        val repository = ArtifactContextHolder.getRepository(context.repositoryDetail.category)
        nodeList.forEach {
            context.putAttribute(FULL_PATH, it[NODE_FULL_PATH] as String)
            val artifactInputStream = repository.query(context) as ArtifactInputStream
            artifactResourceList.add(
                ArtifactResource(
                    artifactInputStream,
                    it[NODE_NAME] as String,
                    null,
                    ArtifactChannel.LOCAL
                )
            )
        }
        HelmZipResponseWriter.write(artifactResourceList)
    }

    companion object {
        const val SLEEP_MILLIS = 20L
        val logger: Logger = LoggerFactory.getLogger(ChartRepositoryServiceImpl::class.java)

        fun convertDateTime(timeStr: String): String {
            val localDateTime = LocalDateTime.parse(timeStr, DateTimeFormatter.ISO_DATE_TIME)
            return localDateTime.format(DateTimeFormatter.ofPattern(DATA_TIME_FORMATTER))
        }
    }
}