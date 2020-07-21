package com.tencent.bkrepo.common.artifact.repository.remote

import com.tencent.bkrepo.common.artifact.api.ArtifactFile
import com.tencent.bkrepo.common.artifact.config.PROXY_AUTHORIZATION
import com.tencent.bkrepo.common.artifact.pojo.configuration.remote.ProxyConfiguration
import com.tencent.bkrepo.common.artifact.pojo.configuration.remote.RemoteConfiguration
import com.tencent.bkrepo.common.artifact.pojo.configuration.remote.RemoteCredentialsConfiguration
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactDownloadContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactSearchContext
import com.tencent.bkrepo.common.artifact.repository.context.ArtifactTransferContext
import com.tencent.bkrepo.common.artifact.repository.core.AbstractArtifactRepository
import com.tencent.bkrepo.common.artifact.resolve.file.ArtifactFileFactory
import com.tencent.bkrepo.common.artifact.resolve.response.ArtifactResource
import com.tencent.bkrepo.common.artifact.stream.Range
import com.tencent.bkrepo.common.artifact.stream.toArtifactStream
import com.tencent.bkrepo.common.artifact.util.http.BasicAuthInterceptor
import com.tencent.bkrepo.common.artifact.util.http.HttpClientBuilderFactory
import com.tencent.bkrepo.common.storage.core.StorageService
import com.tencent.bkrepo.repository.api.NodeResource
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.service.NodeCreateRequest
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import java.net.InetSocketAddress
import java.net.Proxy
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 *
 * @author: carrypan
 * @date: 2019/11/26
 */
abstract class RemoteRepository : AbstractArtifactRepository() {

    @Autowired
    lateinit var nodeResource: NodeResource

    @Autowired
    lateinit var storageService: StorageService

    override fun search(context: ArtifactSearchContext): Any? {
        val remoteConfiguration = context.repositoryConfiguration as RemoteConfiguration
        val httpClient = createHttpClient(remoteConfiguration)
        val downloadUri = generateRemoteDownloadUrl(context)
        val request = Request.Builder().url(downloadUri).build()
        val response = httpClient.newCall(request).execute()
        return if (checkResponse(response)) {
            response.body()!!.string()
        } else null
    }

    override fun onDownload(context: ArtifactDownloadContext): ArtifactResource? {
        return getCacheArtifactResource(context) ?: run {
            val remoteConfiguration = context.repositoryConfiguration as RemoteConfiguration
            val httpClient = createHttpClient(remoteConfiguration)
            val downloadUri = generateRemoteDownloadUrl(context)
            val request = Request.Builder().url(downloadUri).build()
            val response = httpClient.newCall(request).execute()
            return if (checkResponse(response)) {
                val artifactFile = createTempFile(response.body()!!)
                val nodeInfo = putArtifactCache(context, artifactFile)
                val size = artifactFile.getSize()
                val artifactStream = artifactFile.getInputStream().toArtifactStream(Range.full(size))
                return ArtifactResource(artifactStream, determineArtifactName(context), nodeInfo)
            } else null
        }
    }

    /**
     * 尝试读取缓存的远程构件
     */
    private fun getCacheArtifactResource(context: ArtifactDownloadContext): ArtifactResource? {
        val remoteConfiguration = context.repositoryConfiguration as RemoteConfiguration
        val cacheConfiguration = remoteConfiguration.cacheConfiguration
        if (!cacheConfiguration.cacheEnabled) return null

        val nodeInfo = getCacheNodeInfo(context) ?: return null
        if (nodeInfo.folder) return null
        val createdDate = LocalDateTime.parse(nodeInfo.createdDate, DateTimeFormatter.ISO_DATE_TIME)
        val age = Duration.between(createdDate, LocalDateTime.now()).toMinutes()
        return if (age <= cacheConfiguration.cachePeriod) {
            storageService.load(nodeInfo.sha256!!, Range.full(nodeInfo.size), context.storageCredentials)?.run {
                logger.debug("Cached remote artifact[${context.artifactInfo}] is hit.")
                ArtifactResource(this, determineArtifactName(context), nodeInfo)
            }
        } else null
    }

    /**
     * 尝试获取缓存的远程构件节点
     */
    private fun getCacheNodeInfo(context: ArtifactDownloadContext): NodeInfo? {
        val artifactInfo = context.artifactInfo
        val repositoryInfo = context.repositoryInfo
        return nodeResource.detail(repositoryInfo.projectId, repositoryInfo.name, artifactInfo.artifactUri).data?.nodeInfo
    }

    /**
     * 将远程拉取的构件缓存本地
     */
    protected fun putArtifactCache(context: ArtifactDownloadContext, artifactFile: ArtifactFile): NodeInfo? {
        val remoteConfiguration = context.repositoryConfiguration as RemoteConfiguration
        val cacheConfiguration = remoteConfiguration.cacheConfiguration
        return if (cacheConfiguration.cacheEnabled) {
            val nodeCreateRequest = getCacheNodeCreateRequest(context, artifactFile)
            storageService.store(nodeCreateRequest.sha256!!, artifactFile, context.storageCredentials)
            nodeResource.create(nodeCreateRequest).data
        } else null
    }

    /**
     * 获取缓存节点创建请求
     */
    open fun getCacheNodeCreateRequest(context: ArtifactDownloadContext, artifactFile: ArtifactFile): NodeCreateRequest {
        val artifactInfo = context.artifactInfo
        val repositoryInfo = context.repositoryInfo
        val sha256 = artifactFile.getFileSha256()
        val md5 = artifactFile.getFileMd5()
        return NodeCreateRequest(
            projectId = repositoryInfo.projectId,
            repoName = repositoryInfo.name,
            folder = false,
            fullPath = artifactInfo.artifactUri,
            size = artifactFile.getSize(),
            sha256 = sha256,
            md5 = md5,
            overwrite = true,
            operator = context.userId
        )
    }

    /**
     * 创建http client
     */
    open fun createHttpClient(configuration: RemoteConfiguration): OkHttpClient {
        val builder = HttpClientBuilderFactory.create()
        builder.readTimeout(configuration.networkConfiguration.readTimeout, TimeUnit.MILLISECONDS)
        builder.connectTimeout(configuration.networkConfiguration.connectTimeout, TimeUnit.MILLISECONDS)
        builder.proxy(createProxy(configuration.networkConfiguration.proxy))
        builder.proxyAuthenticator(createProxyAuthenticator(configuration.networkConfiguration.proxy))
        createBasicAuthInterceptor(configuration.credentialsConfiguration)?.let { builder.addInterceptor(it) }
        return builder.build()
    }

    /**
     * 创建代理
     */
    open fun createProxy(configuration: ProxyConfiguration?): Proxy {
        return configuration?.let { Proxy(Proxy.Type.HTTP, InetSocketAddress(it.host, it.port)) } ?: Proxy.NO_PROXY
    }

    /**
     * 创建代理身份认证
     */
    open fun createProxyAuthenticator(configuration: ProxyConfiguration?): Authenticator {
        return configuration?.let {
            if (it.username.isNullOrBlank() || it.password.isNullOrBlank()) {
                return Authenticator.NONE
            }
            configuration.password
            Authenticator { _, response ->
                val credential = Credentials.basic(it.username!!, it.password!!)
                response.request().newBuilder().header(PROXY_AUTHORIZATION, credential).build()
            }
        } ?: Authenticator.NONE
    }

    /**
     * 创建身份认证
     */
    open fun createBasicAuthInterceptor(configuration: RemoteCredentialsConfiguration?): Interceptor? {
        return configuration?.let {
            return BasicAuthInterceptor(
                it.username,
                it.password
            )
        }
    }

    /**
     * 生成远程构件下载url
     */
    open fun generateRemoteDownloadUrl(context: ArtifactTransferContext): String {
        val remoteConfiguration = context.repositoryConfiguration as RemoteConfiguration
        val artifactUri = context.artifactInfo.artifactUri
        return remoteConfiguration.url.trimEnd('/') + artifactUri
    }

    /**
     * 检查下载响应
     */
    open fun checkResponse(response: Response): Boolean {
        if (!response.isSuccessful) {
            logger.warn("Download artifact from remote failed: [${response.code()}]")
            return false
        }
        return true
    }

    /**
     * 创建临时文件并将响应体写入文件
     */
    protected fun createTempFile(body: ResponseBody): ArtifactFile {
        return ArtifactFileFactory.build(body.byteStream())
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RemoteRepository::class.java)
    }
}
