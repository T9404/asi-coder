package com.example.documentationagentclient.controller

import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component

@Component
class GenericController {

    @Tool(name = "generic_tool", description = "A generic tool for demonstration purposes")
    fun genericTool(@ToolParam(required = true, description = "Input parameter") input: String): String {
        return "Processed input: $input"
    }
}