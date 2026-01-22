package com.example.documentationagentclient.core

import org.springframework.ai.image.ImagePrompt
import org.springframework.ai.image.ImageResponse
import org.springframework.ai.openai.OpenAiImageModel
import org.springframework.ai.openai.OpenAiImageOptions
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component


@Component
class GenericController(
    private val confluenceSaveService: ConfluenceSaveService,
    private val openaiImageModel: OpenAiImageModel,
    private val pmNotifier: ProjectManagerNotifier,
) {

    @Tool(name = "generate_documentation", description = "Generates documentation image from description and saves it to Confluence")
    fun generateDocumentation(
        @ToolParam(required = true, description = "Issue identifier") issueId: String,
        @ToolParam(required = true, description = "Description of the image to generate") description: String
    ): ImageResponse? {
        try {
            log.info("Generating image with description")

            val prompt = """
                Generate EXACTLY ONE UML CLASS DIAGRAM image.

                PRIMARY GOAL:
                    - Reproduce the classes/interfaces/enums and their names EXACTLY as they appear in INPUT.
                    - Reproduce fields and methods EXACTLY as they appear in INPUT (names, visibility, parameters, return types when present).
                    - Draw ONLY relationships that are explicitly present in INPUT (extends/implements, composition/aggregation markers, associations, dependencies).

                STYLE: PLANTUML-LIKE (MANDATORY)
                    - Black lines only, white background. No colors, no gradients, no shadows, no icons, no decorative elements.
                    - Classic UML class boxes with 3 compartments: Name / Attributes / Methods.
                    - Use PlantUML conventions for notation:
                    - Class header: ClassName (or <<interface>> InterfaceName, <<enum>> EnumName when explicit).
                    - Attributes: +public, -private, #protected visibility markers when visibility is explicit; otherwise omit marker.
                    - Methods: methodName(paramName: Type): ReturnType when types are explicit; otherwise keep only methodName(...) exactly as shown.
                    - Straight connectors, no curves, no 3D, no perspective.
                    - High contrast, sharp readable text. Keep layout compact but readable.

                STRICT ANTI-HALLUCINATION (HARD RULES)
                    - DO NOT invent any class/interface/enum names.
                    - DO NOT invent members (fields/methods) that are not explicitly present.
                    - DO NOT infer relationships from naming or typical architecture. If not explicit, omit.
                    - If INPUT is incomplete/ambiguous, leave the missing parts out rather than guessing.

                OUTPUT CONSTRAINTS
                    - Output must be a SINGLE IMAGE containing ONE diagram.
                    - No extra commentary, no captions, no legends, no additional text outside the diagram.

                INPUT (verbatim, authoritative):
                $description
            """.trimIndent()

            val response = openaiImageModel.call(
                ImagePrompt(
                    prompt,
                    OpenAiImageOptions.builder()
                        .quality("hd")
                        .width(128)
                        .height(128)
                        .N(1)
                        .build()
                )
            )

            val imageUrl = response.result.output.url
            confluenceSaveService.save(issueId, description, imageUrl)
            return response
        } catch (e: Exception) {
            log.warn("Error during generating image $issueId", e)
            pmNotifier.notify(issueId, e.message ?: "Unknown error")
            return null
        }
    }

    companion object {
        private val log = org.slf4j.LoggerFactory.getLogger(GenericController::class.java)
    }

}
