package com.example.documentationagentclient.core

import com.example.documentationagentclient.dto.IssueOperationResult
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.converter.BeanOutputConverter
import org.springframework.stereotype.Service

@Service
class AppCore (
    private val converter: BeanOutputConverter<IssueOperationResult?>,
    private val chatClient: ChatClient,
) {

    fun processIssue(issueId: String): IssueOperationResult {
        val format: String = converter.getFormat()
        val content: String? = chatClient.prompt()
            .system(
                """
                You are an agent that MAY call tools when needed.
                
                After finishing (including any tool calls), return the FINAL answer strictly in this format:
                $format
                
                Return only the object. No markdown. No extra text.
                """.trimIndent()
            )
            .user(
                """
                issueId=$issueId
                """.trimIndent()
            )
            .call()
            .content()

        return converter.convert(content?: "")
    }


}