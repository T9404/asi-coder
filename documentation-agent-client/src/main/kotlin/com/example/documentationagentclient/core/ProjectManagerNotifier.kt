package com.example.documentationagentclient.core

import org.springframework.ai.chat.client.ChatClient
import org.springframework.beans.factory.ObjectProvider
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
class ProjectManagerNotifier(private val chatClient: ObjectProvider<ChatClient>) {

    @Async("projectManagerExecutor")
    fun notify(issueId: String, errorMessage: String) {
        val chat = chatClient.getIfAvailable() ?: return

        val response = chat.prompt(
            """
            An error occurred while generating the image for issue $issueId: $errorMessage
            Please notify the project manager about this failure, including the issue ID and error details.
            Please call issue_operation tool with failure information.
            """.trimIndent()
        ).call().content()

        log.info("Notification sent to project manager: {}", response)
    }

    companion object {
        private val log = org.slf4j.LoggerFactory.getLogger(ProjectManagerNotifier::class.java)
    }
}