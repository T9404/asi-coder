package com.example.projectmanager.agent;

import com.example.projectmanager.dto.Filmography;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/reasoning")
@RequiredArgsConstructor
public class ReasoningController {
    private static final int maxIterations = 10;
    private static final double confidenceThreshold = 0.8;
    // structured reasoning template
    private static final String REASONING_TEMPLATE = """
        You are an expert reasoning agent. Analyze the task and provide structured reasoning.
        
        Respond STRICTLY in this JSON format:
        {
          "thought": "Your analytical thought process",
          "action_needed": true/false,
          "action_type": "SEARCH|ANALYZE|VALIDATE|DECIDE",
          "action_description": "Description of action if needed",
          "confidence": 0.0-1.0,
          "subtasks": ["subtask1", "subtask2"],
          "done": true/false,
          "key_findings": ["finding1", "finding2"]
        }
        
        Guidelines:
        1. Break down complex problems into subtasks
        2. Assess confidence in your reasoning
        3. Identify what information is missing
        4. Specify clear actions when needed
        5. Document key findings at each step
        """;
    private final ChatClient chatClient; // disable tool call only hear
    private final McpSyncClient mcpClient;
    private final ReasoningValidator validator = new ReasoningValidator();

    @GetMapping
    public Filmography generateFilmography() {
        ReasoningContext context = new ReasoningContext();
        context.setTask("Generate new task for a backend team");
        context.setGoal("Produce comprehensive, accurate task with proper composition");

        List<ReasoningStep> steps = new ArrayList<>();
        List<McpSchema.CallToolResult> observations = new ArrayList<>();

        for (int i = 1; i <= maxIterations; i++) {
            log.info("=== Reasoning Cycle {} ===", i);

            // 1. Generate reasoning with context
            ReasoningStep reasoning = generateReasoningStep(context);
            steps.add(reasoning);

            log.info("Thought: {}", reasoning.thought());
            log.info("Confidence: {}", reasoning.confidence());
            log.info("Action needed: {}", reasoning.actionNeeded());

            // 2. Validate reasoning quality
            ValidationResult validation = validator.validate(reasoning);
            if (!validation.isValid()) {
                log.warn("Reasoning validation failed: {}", validation.error());
                context.addFeedback(validation.error());
                continue;
            }

            context.addThought(reasoning.thought());

            // 3. Execute action if needed
            if (reasoning.actionNeeded()) {
                McpSchema.CallToolResult observation = executeAction(reasoning);
                observations.add(observation);
                context.addObservation(observation);
                log.info("Executed action, received observation: {}", observation);
            }

            // 4. Update context with new information
            updateContext(context, reasoning, observations);

            // 5. Check termination conditions
            if (shouldTerminate(reasoning, context, i)) {
                log.info("Terminating reasoning after {} cycles", i);
                break;
            }

            // 6. Add reflection for next cycle
            if (i % 3 == 0) {
                String reflection = reflectOnProgress(context, steps);
                context.addReflection(reflection);
            }
        }

        // Generate final output with enhanced context
        return generateFinalOutput(context, steps, observations);
    }

    private ReasoningStep generateReasoningStep(ReasoningContext context) {
        String enhancedPrompt = """
            %s
            
            Current Context:
            Task: %s
            Goal: %s
            Previous Steps: %d
            Key Findings So Far: %s
            Remaining Questions: %s
            Feedback: %s
            %s
            
            Generate next reasoning step focusing on: %s
            """.formatted(
                REASONING_TEMPLATE,
                context.getTask(),
                context.getGoal(),
                context.getStepCount(),
                context.getKeyFindings().stream().limit(3).toList(),
                context.getUnresolvedQuestions(),
                context.getFeedback().isEmpty() ? "None" : context.getLatestFeedback(),
                mcpClient.listTools(),
                determineFocusArea(context)
        );
        return chatClient.prompt()
                .system(enhancedPrompt)
                .user("Continue reasoning towards the goal.")
                .call()
                .entity(ReasoningStep.class);
    }

    private McpSchema.CallToolResult executeAction(ReasoningStep reasoning) {
        return reasoning.actionNeeded ? mcpClient.callTool(reasoning.request()) : null;
    }

    private void updateContext(ReasoningContext context,
                               ReasoningStep reasoning,
                               List<McpSchema.CallToolResult> observations) {
        // Update key findings
        if (reasoning.keyFindings() != null) {
            context.addKeyFindings(reasoning.keyFindings());
        }

        // Update unresolved questions
        List<String> newQuestions = extractUnresolvedQuestions(reasoning.thought());
        context.addUnresolvedQuestions(newQuestions);

        // Update confidence history
        context.addConfidenceScore(reasoning.confidence());

        // Extract and store entities mentioned
        List<String> entities = extractEntities(reasoning.thought());
        context.addMentionedEntities(entities);
    }

    private List<String> extractUnresolvedQuestions(String thought) {
        if (thought == null || thought.isBlank()) return List.of();

        // 1) Вытаскиваем явные вопросы по '?'
        List<String> questions = new ArrayList<>();
        for (String part : thought.split("\\?")) {
            String q = part.trim();
            if (q.isEmpty()) continue;

            // Восстанавливаем вопросительную форму (чтобы не терять смысл)
            // Берем хвост последнего предложения
            String[] sentences = q.split("[\\n\\r\\.]+");
            String last = sentences[sentences.length - 1].trim();
            if (!last.isEmpty()) questions.add(last + "?");
        }

        // 2) Если вопросов нет — попробуем вытащить “missing/need/to find” как вопросы
        if (questions.isEmpty()) {
            String lower = thought.toLowerCase();
            if (lower.contains("missing") || lower.contains("need") || lower.contains("unknown") || lower.contains("to find")) {
                questions.add("What information is missing?");
            }
        }

        // Ограничим шум
        return questions.stream()
                .map(String::trim)
                .filter(s -> s.length() >= 5)
                .distinct()
                .limit(5)
                .toList();
    }

    private List<String> extractEntities(String thought) {
        if (thought == null || thought.isBlank()) return List.of();

        // Примитив: собираем последовательности слов с Заглавной буквы.
        // Это поймает имена/названия (и иногда мусор). Для компиляции достаточно.
        Pattern p = Pattern.compile("\\b(?:[A-Z][a-z]+(?:\\s+[A-Z][a-z]+){0,3})\\b");
        Matcher m = p.matcher(thought);

        List<String> entities = new ArrayList<>();
        while (m.find()) {
            String e = m.group().trim();
            if (e.length() < 2) continue;
            entities.add(e);
        }

        return entities.stream()
                .distinct()
                .limit(10)
                .toList();
    }


    private boolean shouldTerminate(ReasoningStep reasoning,
                                    ReasoningContext context,
                                    int currentIteration) {
        // Multiple termination conditions
        if (reasoning.done()) {
            return true;
        }

        if (reasoning.confidence() >= confidenceThreshold &&
                context.getUnresolvedQuestions().isEmpty()) {
            return true;
        }

        if (currentIteration >= maxIterations) {
            log.warn("Max iterations reached without completion");
            return true;
        }

        // Check for stagnation (last 3 steps have similar content)
        if (isReasoningStagnant(context.getRecentThoughts(), 3)) {
            log.warn("Reasoning appears stagnant - terminating");
            return true;
        }

        return false;
    }

    private String reflectOnProgress(ReasoningContext context, List<ReasoningStep> steps) {
        // Generate periodic reflection to improve reasoning
        return chatClient.prompt()
                .system("""
                        Reflect on the reasoning progress so far. Identify:
                        1. What has been accomplished
                        2. What remains unclear
                        3. Potential blind spots
                        4. Suggestions for more effective reasoning
                        
                        Be concise and actionable.
                        """)
                .user("""
                        Reasoning steps taken: %s
                        Current findings: %s
                        Unresolved questions: %s
                        """.formatted(
                        steps.stream()
                                .map(ReasoningStep::thought)
                                .collect(Collectors.joining(" -> ")),
                        context.getKeyFindings(),
                        context.getUnresolvedQuestions()
                ))
                .call()
                .content();
    }

    private Filmography generateFinalOutput(ReasoningContext context,
                                            List<ReasoningStep> steps,
                                            List<McpSchema.CallToolResult> observations) {

        // Create comprehensive context for final generation
        String reasoningTrace = steps.stream()
                .map(step -> """
                    Step: %s
                    Confidence: %.2f
                    Key Findings: %s
                    """.formatted(
                        step.thought(),
                        step.confidence(),
                        step.keyFindings() != null ? step.keyFindings() : List.of()
                ))
                .collect(Collectors.joining("\n---\n"));

        return chatClient.prompt()
                .system("""
                    You are a filmography expert. Generate a comprehensive filmography 
                    based on the reasoning process and observations.
                    
                    Structure the output as:
                    1. Actor Overview
                    2. Film Categories (e.g., Major Roles, Supporting Roles, Cameos)
                    3. Timeline Analysis
                    4. Notable Collaborations
                    5. Career Highlights
                    
                    Ensure accuracy and completeness.
                    """)
                .user("""
                    Generate filmography for a random actor based on:
                    
                    REASONING PROCESS:
                    %s
                    
                    OBSERVATIONS:
                    %s
                    
                    KEY FINDINGS:
                    %s
                    
                    FINAL INSTRUCTIONS:
                    Use all available information. If any gaps remain, acknowledge them.
                    Add analytical insights where possible.
                    """.formatted(
                        reasoningTrace,
                        observations,
                        context.getKeyFindings()
                ))
                .call()
                .entity(Filmography.class);
    }

    // Helper methods
    private String determineFocusArea(ReasoningContext context) {
        if (context.getUnresolvedQuestions().isEmpty()) {
            return "Synthesizing findings and preparing final output";
        }

        // Determine focus based on remaining questions
        List<String> questions = context.getUnresolvedQuestions();
        String latestQuestion = questions.get(questions.size() - 1);

        if (latestQuestion.contains("film")) {
            return "Researching specific films and roles";
        } else if (latestQuestion.contains("year") || latestQuestion.contains("time")) {
            return "Establishing timeline and chronology";
        } else if (latestQuestion.contains("category") || latestQuestion.contains("type")) {
            return "Categorizing roles and films";
        }

        return "General research and information gathering";
    }

    private boolean isReasoningStagnant(List<String> recentThoughts, int window) {
        if (recentThoughts.size() < window) return false;

        List<String> lastN = recentThoughts.subList(
                Math.max(0, recentThoughts.size() - window),
                recentThoughts.size()
        );

        // Simple similarity check (in production, use embedding similarity)
        Set<String> uniqueWords = new HashSet<>();
        lastN.forEach(thought ->
                uniqueWords.addAll(Arrays.asList(thought.split("\\s+"))));

        double diversityRatio = (double) uniqueWords.size() /
                lastN.stream().mapToInt(t -> t.split("\\s+").length).sum();

        return diversityRatio < 0.3; // If 70% of words are repeats
    }

    @Getter
    @Setter
    static class ReasoningContext {
        private String task;
        private String goal;
        private final List<String> keyFindings = new ArrayList<>();
        private final Deque<String> recentThoughts = new ArrayDeque<>();
        private final List<String> unresolvedQuestions = new ArrayList<>();
        private final List<String> feedback = new ArrayList<>();
        private final List<String> reflections = new ArrayList<>();
        private final List<McpSchema.CallToolResult> observations = new ArrayList<>();
        private final List<Double> confidenceScores = new ArrayList<>();
        private final Set<String> mentionedEntities = new LinkedHashSet<>();

        private int stepCount = 0;

        public List<String> getRecentThoughts() {
            return new ArrayList<>(recentThoughts);
        }

        public void addFeedback(String item) {
            if (item != null && !item.isBlank()) feedback.add(item);
        }

        public void addReflection(String reflection) {
            if (reflection != null && !reflection.isBlank()) reflections.add(reflection);
        }

        public String getLatestFeedback() {
            return feedback.isEmpty() ? null : feedback.get(feedback.size() - 1);
        }

        public void addObservation(McpSchema.CallToolResult obs) {
            if (obs != null) observations.add(obs);
        }

        public void addKeyFindings(List<String> findings) {
            if (findings == null) return;
            for (String f : findings) {
                if (f != null && !f.isBlank()) keyFindings.add(f);
            }
        }

        public void addUnresolvedQuestions(List<String> questions) {
            if (questions == null) return;
            for (String q : questions) {
                if (q != null && !q.isBlank()) unresolvedQuestions.add(q);
            }
        }

        public void addConfidenceScore(Double score) {
            if (score == null) return;
            confidenceScores.add(score);
        }

        public void addMentionedEntities(List<String> entities) {
            if (entities == null) return;
            for (String e : entities) {
                if (e != null && !e.isBlank()) mentionedEntities.add(e);
            }
        }

        public void addThought(String thought) {
            if (thought == null || thought.isBlank()) return;
            recentThoughts.addLast(thought);
            while (recentThoughts.size() > 5) recentThoughts.removeFirst();
        }

    }

    public record ValidationResult(boolean isValid, String error, List<String> suggestions) {
        public static ValidationResult valid() {
            return new ValidationResult(true, null, List.of());
        }
    }

    public static class ReasoningValidator {
        public ValidationResult validate(ReasoningStep step) {
            List<String> errors = new ArrayList<>();
            List<String> suggestions = new ArrayList<>();

            // Check thought quality
            if (step.thought().length() < 10) {
                errors.add("Thought too brief");
                suggestions.add("Expand reasoning with more analysis");
            }

            if (step.thought().contains("I don't know") ||
                    step.thought().contains("not sure")) {
                suggestions.add("Re-frame uncertainty as specific questions to investigate");
            }

            // Check confidence alignment
            if (step.confidence() > 0.9 && step.actionNeeded()) {
                suggestions.add("High confidence with action needed - consider if action is necessary");
            }

            return errors.isEmpty()
                    ? ValidationResult.valid()
                    : new ValidationResult(false,
                    String.join("; ", errors),
                    suggestions);
        }
    }

    public record ReasoningStep(
            String thought,
            boolean actionNeeded,
            double confidence,
            boolean done,
            McpSchema.CallToolRequest request,
            List<String> keyFindings
    ) {}

}
