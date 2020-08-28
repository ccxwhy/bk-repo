package com.tencent.bkrepo.common.artifact.event

import com.tencent.bkrepo.common.artifact.webhook.WebHookService
import com.tencent.bkrepo.common.storage.event.StoreFailureEvent
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.event.EventListener

/**
 * 构件相关事件监听器
 */
class ArtifactEventListener {

    @Autowired
    private lateinit var webHookService: WebHookService

    @EventListener(StoreFailureEvent::class)
    fun listen(event: StoreFailureEvent) {
        event.apply {
            logger.error("[StoreFailureEvent]failed to store file[$filename] on [$storageCredentials].", exception)
        }
    }

    @EventListener(ArtifactUploadedEvent::class)
    fun listen(event: ArtifactUploadedEvent) {
        webHookService.hook(event.context, event.type)
    }

    @EventListener(ArtifactRemovedEvent::class)
    fun listen(event: ArtifactRemovedEvent) {
        webHookService.hook(event.context, event.type)
    }

    @EventListener(ArtifactUpdatedEvent::class)
    fun listen(event: ArtifactUpdatedEvent) {
        webHookService.hook(event.context, event.type)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ArtifactEventListener::class.java)
    }
}
