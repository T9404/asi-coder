package com.example.confluencemcpserver

import org.springframework.util.StringUtils

data class ConfluenceActionResult(
    val id: String?,
    val type: String?,
    val title: String?,
    val version: Int,
    val url: String?,
    val spaceKey: String?,
    val containerId: String?
) {
    companion object {
        fun fromContent(content: ConfluenceContent, properties: ConfluenceProperties): ConfluenceActionResult {
            val link = resolveWebUrl(content.links, properties.baseUrl)
            val space = content.space?.key
            val container = content.container?.id
            val versionNumber = content.version?.number ?: 0

            return ConfluenceActionResult(
                id = content.id,
                type = content.type,
                title = content.title,
                version = versionNumber,
                url = link,
                spaceKey = space,
                containerId = container
            )
        }

        private fun resolveWebUrl(
            links: Map<String, Any?>?,
            fallbackBaseUrl: String?
        ): String? {
            if (links.isNullOrEmpty()) {
                return null
            }
            val base = links["base"]?.toString()
            val webUi = links["webui"]?.toString()
            val tinyUi = links["tinyui"]?.toString()
            val root = if (StringUtils.hasText(base)) base else fallbackBaseUrl

            return when {
                StringUtils.hasText(webUi) -> root + webUi
                StringUtils.hasText(tinyUi) -> root + tinyUi
                else -> root
            }
        }
    }
}
