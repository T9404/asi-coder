package com.example.projectmanager.controller;

import com.example.projectmanager.dto.Filmography;
import io.modelcontextprotocol.client.McpSyncClient;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class TestController {
    private static final Logger log = LoggerFactory.getLogger(TestController.class);
    private final ChatClient chatClient;
    private final McpSyncClient mcpClient;


    @GetMapping
    public Filmography test() {
        List<ReasoningStep> steps = new ArrayList<>();
        Set<String> seenThoughts = new HashSet<>();
        String previousThought = "Start planning a filmography generation for a random actor";

        int maxSteps = 10;
        for (int i = 1; i <= maxSteps; i++) {
            try {
                ReasoningStep step = chatClient.prompt()
                        .system("""
                        You are iteratively reasoning before generating a filmography.
                        Requirements:
                        1. Each thought must be substantial (at least 15 words)
                        2. Progress logically from previous thoughts
                        3. When ready to generate, set done=true
                        
                        Reply ONLY as JSON: {"thought":"<next-thought>","done":true|false}
                        """)
                        .user("""
                        Previous thought: %s
                        
                        Previous reasoning steps: %s
                        
                        Produce the next reasoning thought.
                        Ensure it's substantial and moves forward.
                        Set done=true only if fully ready to generate the filmography.
                        """.formatted(previousThought,
                                steps.stream().map(ReasoningStep::thought).collect(Collectors.joining(" -> "))))
                        .call()
                        .entity(ReasoningStep.class);

                // Валидация 1: Проверка на минимальную длину мысли
                if (step.thought().split("\\s+").length < 15) {
                    log.warn("Step {} rejected: thought too short ({} words)",
                            i, step.thought().split("\\s+").length);
                    continue; // Пропускаем этот шаг, но продолжаем цикл
                }

                // Валидация 2: Проверка на циклы (повторяющиеся мысли)
                String normalizedThought = step.thought().toLowerCase().trim();
                if (seenThoughts.contains(normalizedThought)) {
                    log.warn("Step {} rejected: thought too similar to previous", i);
                    continue;
                }

                // Валидация 3: Проверка прогресса (должен ссылаться на предыдущие мысли)
                if (i > 1 && !step.thought().toLowerCase().contains(previousThought.toLowerCase().split("\\s+")[0])) {
                    log.warn("Step {}: weak connection to previous thought", i);
                    // Можно продолжать, но логируем предупреждение
                }

                steps.add(step);
                seenThoughts.add(normalizedThought);
                log.info("Reasoning step {}: {} (done={})", i, step.thought(), step.done());
                previousThought = step.thought();

                if (step.done()) {
                    log.info("Reasoning completed in {} steps", i);
                    break;
                }

                // Валидация 4: Если достигли максимума шагов, принудительно завершаем
                if (i == maxSteps) {
                    log.warn("Max steps reached ({}) without completion", maxSteps);
                }

            } catch (Exception e) {
                log.error("Error in reasoning step {}: {}", i, e.getMessage());
                // Можно добавить fallback или break
            }
        }

        // Фильтрация null-шагов (если какие-то были пропущены)
        List<String> validThoughts = steps.stream()
                .filter(Objects::nonNull)
                .map(ReasoningStep::thought)
                .toList();

        String stepsSummary = validThoughts.stream()
                .reduce((a, b) -> a + " -> " + b)
                .orElse("No valid reasoning steps");

        // Optional: Добавить REACT-подобный ревью шаг
        if (steps.isEmpty() || steps.stream().noneMatch(ReasoningStep::done)) {
            log.info("No completion reached, adding review step");
            stepsSummary = addReviewStep(stepsSummary, chatClient);
        }

        Filmography filmography = chatClient.prompt()
                .system("""
                Use the prior reasoning to produce the final filmography.
                Generate a realistic filmography for a random actor with:
                - Actor name and basic info
                - 5-10 notable films with years
                - Awards if any
                """)
                .user("""
                Generate the filmography for a random actor.
                Reasoning trace: %s
                Produce the final filmography in the required format.
                """.formatted(stepsSummary))
                .call()
                .entity(Filmography.class);

        return filmography;
//        List<ReasoningStep> steps = new ArrayList<>();
//        String previousThought = "Start planning a filmography generation for a random actor";
//
//        // короче надо забить хуй и сделать тока валидацию if'ами ответа проверку на циклы (типа кол-во слов), плюс использование старых мыслей, валидация циклов
//        // вызов tools пуст будет у chatclient и чекни что он может вызывать до reasoning (нет похуй тогда но мб можна что-то сделать)
//        // и можешь еще добавить re-act по желанию (в конце другая модель делает ревью если нет то пусть перегенерит) все больше не нада ничего выдумывать
//        for (int i = 1; i <= 5; i++) {
//            ReasoningStep step = chatClient.prompt()
//                    .system("You are iteratively reasoning before generating a filmography. Reply ONLY as JSON: {\"thought\":\"<next-thought>\",\"done\":true|false}.")
//                    .user("""
//                            Previous thought: %s
//                            Produce the next reasoning thought. Set done=true if ready to generate the filmography now.""".formatted(previousThought))
//                    .call()
//                    .entity(ReasoningStep.class);
//
//            steps.add(step);
//            log.info("Reasoning step {}: {} (done={})", i, step.thought(), step.done());
//            previousThought = step.thought();
//
//            if (step.done()) {
//                break;
//            }
//        }
//
//        String stepsSummary = steps.stream()
//                .map(ReasoningStep::thought)
//                .reduce((a, b) -> a + " -> " + b)
//                .orElse(previousThought);
//
//        return chatClient.prompt()
//                .system("Use the prior reasoning to produce the final filmography.")
//                .user("""
//                        Generate the filmography for a random actor.
//                        Reasoning trace: %s
//                        If the reasoning already concluded, proceed directly to the final answer.""".formatted(stepsSummary))
//                .call()
//                .entity(Filmography.class);
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

    private record ReasoningStep(String thought, boolean done) {
    }

    @PostMapping
    public String testMcpServer() {
        log.info("MCP Server test endpoint called");
        String issueId = chatClient.prompt()
                .system("You are a assistant, you have connected mcp server to use youtrack api.")
                .user("""
                        Call your MCP Server , Create a new issue with summary 'Test Issue' and description 'This is a test issue created via MCP integration.' in 'test' project (use it as projectIdOrKey) and return the issue ID
                        {
                          "method": "tools/call",
                          "params": {
                            "name": "create_issue",
                            "arguments": {
                              "projectIdOrKey": "test",
                              "summary": "важная задача",
                              "description": "описание"
                            }
                          }
                        }
                        """
                )
                .call()
                .content();
        log.info("Created issue with ID: {}", issueId);
        return issueId;
    }
}
