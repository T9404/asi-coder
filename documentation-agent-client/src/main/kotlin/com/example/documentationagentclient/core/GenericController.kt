package com.example.documentationagentclient.core

import io.modelcontextprotocol.client.McpSyncClient
import org.springframework.ai.image.ImagePrompt
import org.springframework.ai.image.ImageResponse
import org.springframework.ai.openai.OpenAiImageModel
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component


@Component
class GenericController(
    private val confluenceSaveService: ConfluenceSaveService,
    private val openaiImageModel: OpenAiImageModel,
    private val projectManagerMcpClient : McpSyncClient
) {

    @Tool(name = "generic_tool", description = "A generic tool for demonstration purposes")
    fun genericTool(@ToolParam(required = true, description = "Input parameter") input: String): String {
        return "Processed input: $input"
    }

    @Tool(name = "generate_image", description = "Generates an image based on the provided description")
    fun generateImage(
        @ToolParam(required = true, description = "Issue identifier") issueId: String,
        @ToolParam(required = true, description = "Description of the image to generate") description: String
    ): ImageResponse? {
        try {
            log.info("Generating image with description")

            val response: ImageResponse = openaiImageModel.call(
                ImagePrompt(
                    """
            Generate ONE UML CLASS DIAGRAM image from the provided source code snippet(s), regardless of programming language (Java, Kotlin, TypeScript, React, etc.).
            GOAL:
            - Visualize the static structure that is explicitly present in the snippet: types and their relationships.
            - Produce a diagram that is correct for the snippet, not “complete” for a whole project.

            STRICT ANTI-HALLUCINATION RULE:
            - DO NOT invent missing types or relationships.
            """.trimIndent()
                )
            )

            val imageUrl = response.result.output.url
            confluenceSaveService.save(issueId, description, imageUrl)
            return response
        } catch (e: Exception) {
            log.warn("Error during generating image $issueId", e)
            //projectManagerMcpClient.callTool()
            return null
        }
    }

    companion object {
        private val log = org.slf4j.LoggerFactory.getLogger(GenericController::class.java)
    }

}
