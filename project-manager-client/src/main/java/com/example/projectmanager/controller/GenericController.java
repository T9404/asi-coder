package com.example.projectmanager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
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
    private static final int MAX_REASONING_STEPS = 10;
    private static final int MIN_THOUGHT_WORDS = 15;
    private static final String ISSUE_OPERATION_SYSTEM_PROMPT = """
        You are iteratively reasoning before executing an issue operation.

        Output MUST be valid JSON that conforms to the following JSON Schema.
        No markdown. No extra keys. No trailing commentary.

        JSON Schema (draft 2020-12):
        {
            "$schema": "https://json-schema.org/draft/2020-12/schema",
            "title": "IssueOperationReasoningStep",
            "type": "object",
            "additionalProperties": false,
            "required": ["thought", "done"],
            "properties": {
                "thought": {
                "type": "string",
                "minLength": 20,
                "description": "Next reasoning step. Must be substantial and build on prior steps."
            },
            "done": {
                "type": "boolean",
                "description": "true only when fully ready to execute the issue operation."
            }
        }

        Behavior rules:
            - Each step must move the plan forward; no rephrasing.
            - Keep thought concise but non-trivial.
            - If the user request contains an error from another system, add a comment to the issue describing the error and the agent using MCP tool call add_issue_comment.
            - Available transitions: backlog, develop, review, test, staging, done.

        Return ONLY the JSON object:
        {"thought":"...","done":false}
        """;

    private static final String ISSUE_OPERATION_USER_PROMPT_TEMPLATE = """
        Previous thought: %s
        
        Previous reasoning steps: %s
        
        Produce the next reasoning thought.
        Ensure it's substantial and moves forward.
        Set done=true only if fully ready to execute the issue operation.
        """;
    private final ObjectProvider<ChatClient> chatClientProvider;
    private final Counter successCounter;
    private final Counter failureCounter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GenericController(@Lazy ObjectProvider<ChatClient> chatClientProvider, MeterRegistry meterRegistry) {
        this.chatClientProvider = chatClientProvider;
        this.successCounter = Counter.builder("tool.issue_operation.execution")
                .tag("operation", "success")
                .register(meterRegistry);
        this.failureCounter = Counter.builder("tool.issue_operation.execution")
                .tag("operation", "failure")
                .register(meterRegistry);
    }

    @Timed(value = "tool.issue_operation.execution.time", description = "Time taken to execute issue operation tool", histogram = true)
    @Tool(
            name = "issue_operation",
            description = """
                Execute an operation on an issue by id.
                Operations: show/summary, status, comment, assign, transition(backlog/develop/review/test/staging/done).
                Input: issueId + a natural-language request describing the operation.
                Output: the result of the operation (or an error message).
                """
    )
    public String execute(
            @ToolParam(description = "Issue id or readable id.") String issueId,
            @ToolParam(description = "Human request: what to do with the issue. Examples: 'show', 'status', 'comment: ...', 'assign to bob', 'close'") String request
    ) {
        try {
            String response = executeInternal(issueId, request);
            successCounter.increment();
            return response;
        } catch (Exception e) {
            log.error("Failed to execute issue operation: {}", e.getMessage());
            failureCounter.increment();
            return "Error executing issue operation";
        }
    }

    private String executeInternal(String issueId, String request) {
        ReasoningResult reasoning = performReasoning(issueId, request);
        return executeOperation(issueId, reasoning.getThoughtsSummary());
    }

    private ReasoningResult performReasoning(String issueId, String request) {
        log.info("Start planning how to '{}' for issue {}", request, issueId);

        List<ReasoningStep> steps = new ArrayList<>();
        Set<String> seenThoughts = new LinkedHashSet<>();
        String currentThought = "Start planning: " + request + " for issue " + issueId;

        for (int step = 0; step < MAX_REASONING_STEPS; step++) {
            ReasoningStep nextStep = generateReasoningStep(currentThought, steps, step);

            if (isInvalidStep(nextStep, seenThoughts)) {
                log.warn("Step {} rejected: invalid reasoning step", step + 1);
                continue;
            }

            log.info("Current thought: {}", currentThought);
            steps.add(nextStep);
            seenThoughts.add(nextStep.thought());
            currentThought = nextStep.thought();

            if (nextStep.done()) {
                log.info("Reasoning complete after {} steps.", step + 1);
                break;
            }
        }

        log.info("End planning how to '{}' for issue {}", request, issueId);
        return new ReasoningResult(steps, seenThoughts);
    }

    private ReasoningStep generateReasoningStep(String previousThought, List<ReasoningStep> previousSteps, int stepIndex) {
        try {
            String stepsChain = previousSteps.stream()
                    .map(ReasoningStep::thought)
                    .collect(Collectors.joining(" -> "));
            String prompt = ISSUE_OPERATION_USER_PROMPT_TEMPLATE.formatted(previousThought, stepsChain);

            String response = chatClientProvider.getObject().prompt()
                    .system(ISSUE_OPERATION_SYSTEM_PROMPT)
                    .user(prompt)
                    .call()
                    .content();
            return parseReasoningStep(response);
        } catch (Exception e) {
            throw new RuntimeException("Reasoning step generation failed", e);
        }
    }

    private boolean isInvalidStep(ReasoningStep step, Set<String> seenThoughts) {
        if (step == null) {
            log.warn("Step rejected: step is null, probably due to parsing error");
            return true;
        }

        if (countWords(step.thought()) < MIN_THOUGHT_WORDS) {
            log.warn("Step rejected: thought too short ({} words)", countWords(step.thought()));
            return true;
        }


        if (seenThoughts.contains(step.thought())) {
            log.warn("Step rejected: duplicate thought detected: {}", step.thought());
            return true;
        }

        return false;
    }

    private int countWords(String text) {
        return text.trim().split("\\s+").length;
    }

    private ReasoningStep parseReasoningStep(String json) {
        try {
            return objectMapper.readValue(json, ReasoningStep.class);
        } catch (Exception e) {
            log.error("Failed to parse reasoning step JSON: {}", e.getMessage());
            return null;
        }
    }

    private String executeOperation(String issueId, String reasoningTrace) {
        if (reasoningTrace.isEmpty()) {
            reasoningTrace = "No reasoning steps generated";
        }

        String prompt = """
                Execute issue operation for %s based on reasoning.
                Reasoning trace: %s
                Produce final result.
                """.formatted(issueId, reasoningTrace);

        return chatClientProvider.getObject().prompt()
                .system("Execute issue operation based on reasoning")
                .user(prompt)
                .call()
                .content();
    }

    private record ReasoningResult(List<ReasoningStep> steps, Set<String> seenThoughts) {
        public String getThoughtsSummary() {
            return steps.stream()
                    .map(ReasoningStep::thought)
                    .collect(Collectors.joining(" -> "));
        }
    }

    private record ReasoningStep(String thought, boolean done) {}
}
