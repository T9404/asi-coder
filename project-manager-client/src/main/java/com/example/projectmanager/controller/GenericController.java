package com.example.projectmanager.controller;

import com.example.projectmanager.dto.Filmography;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class GenericController {
    private static final int MAX_STEPS = 10;
    private static final String ISSUE_OPERATION_SYSTEM_PROMPT = """
        You are iteratively reasoning before executing an issue operation.
        Requirements:
            1. Each thought must be substantial
            2. Progress logically from previous thoughts
            3. When ready to execute, set done=true
        Reply ONLY as JSON: {"thought":"<next-thought>","done":true|false}
        When request contains error from another system, please add a comment to the issue describing the error and agent using call to MCP tools (add_issue_comment).
        """;
    private static final String ISSUE_OPERATION_USER_PROMPT_TEMPLATE = """
        Previous thought: %s
        
        Previous reasoning steps: %s
        
        Produce the next reasoning thought.
        Ensure it's substantial and moves forward.
        Set done=true only if fully ready to execute the issue operation.
        """;
    private final ObjectProvider<ChatClient> chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GenericController(@Lazy ObjectProvider<ChatClient> chatClientProvider) {
        this.chatClient = chatClientProvider;
    }

    @Tool(
            name = "issue_operation",
            description = """
                Execute an operation on an issue by id.
                Operations: show/summary, status, comment, assign, transition(close/done/open/in-progress).
                Input: issueId + a natural-language request describing the operation.
                Output: the result of the operation (or an error message).
                """
    )
    public String execute(
            @ToolParam(description = "Issue id or readable id.") String issueId,
            @ToolParam(description = "Human request: what to do with the issue. Examples: 'show', 'status', 'comment: ...', 'assign to bob', 'close'") String request
    ) {
        List<ReasoningStep> steps = new ArrayList<>();
        Set<String> seenThoughts = new HashSet<>();
        String previousThought = "Start planning how to " + request + " for issue " + issueId;

        for (int i = 0; i < MAX_STEPS; i++) {
            try {
                String rawStep = chatClient.getObject().prompt()
                        .system(ISSUE_OPERATION_SYSTEM_PROMPT)
                        .user(ISSUE_OPERATION_USER_PROMPT_TEMPLATE.formatted(previousThought, steps.stream().map(ReasoningStep::thought).collect(Collectors.joining(" -> "))))
                        .call()
                        .content();
                ReasoningStep step = parseReasoningStep(rawStep);

                if (isInvalidReasoningStep(seenThoughts, step, i)) {
                    continue;
                }

                steps.add(step);
                seenThoughts.add(step.thought());
                previousThought = step.thought();

                log.info("Step {} accepted: {}", i + 1, step.thought());

                if (step.done()) {
                    log.info("Reasoning complete after {} steps.", i + 1);
                    break;
                }
            } catch (Exception e) {
                log.error("Error during reasoning step {}: {}", i + 1, e.getMessage());
                break;
            }
        }

        List<String> validThoughts = steps.stream()
                .filter(Objects::nonNull)
                .map(ReasoningStep::thought)
                .toList();

        String stepsSummary = validThoughts.stream()
                .reduce((a, b) -> a + " -> " + b)
                .orElse("No valid reasoning steps");

        if (steps.isEmpty() || steps.stream().noneMatch(ReasoningStep::done)) {
            log.info("No completion reached, adding review step");
            stepsSummary = addReviewStep(stepsSummary, chatClient.getObject());
        }

        return chatClient.getObject().prompt()
                .system("""
                You are to execute the issue operation based on the provided reasoning trace.
                Use the reasoning trace to determine the correct action for the issue operation.
                """)
                .user("""
                Execute the issue operation for issue %s based on the reasoning trace.
                Reasoning trace: %s
                Produce the final result of the issue operation.
                """.formatted(issueId, stepsSummary))
                .call()
                .content();
    }

    private ReasoningStep parseReasoningStep(String rawStep) {
        try {
            return objectMapper.readValue(rawStep, ReasoningStep.class);
        } catch (Exception e) {
            log.warn("Failed to parse reasoning step JSON: {}", e.getMessage());
            return null;
        }
    }

    private boolean isInvalidReasoningStep(Set<String> seenThoughts, ReasoningStep step, int stepIndex) {
        // validation 1: check for null step
        if (step == null) {
            log.warn("Step {} rejected: step is null", stepIndex + 1);
            return true;
        }

        // validation 2: check for minimum thought length
        if (step.thought().split("\\s+").length < 15) {
            log.warn("Step {} rejected: thought too short ({} words)", stepIndex + 1, step.thought().split("\\s+").length);
            return true;
        }

        // validation 3: check for duplicate thoughts
        if (seenThoughts.contains(step.thought())) {
            log.warn("Step {} rejected: duplicate thought detected", stepIndex + 1);
            return true;
        }

        return false;
    }

    private String addReviewStep(String reasoningTrace, ChatClient chatClient) {
        String reviewThought = chatClient.prompt()
                .system("""
                        You are reviewing the prior reasoning steps for completeness and coherence.
                        Identify any gaps or weaknesses and suggest improvements.
                        Reply ONLY with your review thought.
                        """)
                .user("""
                        Reasoning trace: %s
                        
                        Provide your review thought to improve the reasoning.
                        """.formatted(reasoningTrace))
                .call()
                .content();

        return reasoningTrace + " -> Review: " + reviewThought;
    }

    private record ReasoningStep(String thought, boolean done) {}
}
