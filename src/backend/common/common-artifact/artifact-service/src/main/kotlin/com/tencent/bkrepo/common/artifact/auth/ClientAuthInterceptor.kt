package com.tencent.bkrepo.common.artifact.auth

import com.tencent.bkrepo.common.artifact.config.PROJECT_ID
import com.tencent.bkrepo.common.artifact.config.REPO_NAME
import com.tencent.bkrepo.common.artifact.exception.ClientAuthException
import com.tencent.bkrepo.common.artifact.resolve.ArtifactInfoMethodArgumentResolver
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.servlet.HandlerMapping
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * 依赖源客户端认证拦截器
 *
 * @author: carrypan
 * @date: 2019/11/22
 */
class ClientAuthInterceptor: HandlerInterceptorAdapter() {

    @Autowired
    private lateinit var clientAuthHandler: ClientAuthHandler

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val uri = request.requestURI
        val nameValueMap = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) as Map<*, *>
        val projectId = nameValueMap[PROJECT_ID]?.toString()
        val repoName = nameValueMap[REPO_NAME]?.toString()

        return if(clientAuthHandler.needAuthenticate(uri, projectId, repoName)) {
            try {
                val userId = clientAuthHandler.onAuthenticate(request)
                logger.debug("User[$userId] authenticate success.")
                clientAuthHandler.onAuthenticateSuccess(userId, request, response)
                true
            } catch (authException: ClientAuthException) {
                logger.warn("Authenticate failed: $authException")
                clientAuthHandler.onAuthenticateFailed(request, response)
                false
            }
        } else true

    }

    companion object {
        private val logger = LoggerFactory.getLogger(ClientAuthInterceptor::class.java)
    }
}