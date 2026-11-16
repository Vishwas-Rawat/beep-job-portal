package com.beep.jobportal.tag;

import com.beep.jobportal.job.Job;
import com.beep.jobportal.normalize.IndiaLocations;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * One LLM call for one batch of jobs. Only talks to the model and parses JSON;
 * business rules (allowed roles, city names, retries) live in TaggingService.
 */
@Component
public class JobTagger {

    /** What the model said about one post, before validation. */
    public record RawTag(long id, String role, String city, Boolean isFresher) {}

    public record BatchResult(List<RawTag> tags, long inputTokens, long outputTokens) {}

    private final ChatModel chatModel;
    private final ObjectMapper mapper;
    private final TaggingProperties props;
    private final String systemPrompt;

    public JobTagger(ChatModel chatModel, ObjectMapper mapper, TaggingProperties props,
                     IndiaLocations india) throws IOException {
        this.chatModel = chatModel;
        this.mapper = mapper;
        this.props = props;
        // Prompt lives in a file so it can be edited without touching code.
        String template = new ClassPathResource("prompts/tagging-system.txt")
                .getContentAsString(StandardCharsets.UTF_8);
        this.systemPrompt = template
                .replace("[[ROLES]]", String.join(", ", props.roles()))
                .replace("[[CITIES]]", String.join(", ", india.canonicalCities()));
    }

    public String systemPrompt() {
        return systemPrompt;
    }

    public BatchResult tag(List<Job> batch) {
        var options = OpenAiChatOptions.builder()
                .temperature(0.0)
                // JSON mode: the API guarantees syntactically valid JSON
                .responseFormat(ResponseFormat.builder().type(ResponseFormat.Type.JSON_OBJECT).build());
        if (props.reasoningEffort() != null && !props.reasoningEffort().isBlank()) {
            options.reasoningEffort(props.reasoningEffort());
        }

        // Messages are built directly (not via a prompt template), so braces
        // inside job descriptions can never break template rendering.
        Prompt prompt = new Prompt(
                List.of(new SystemMessage(systemPrompt), new UserMessage(postsText(batch))),
                options.build());

        ChatResponse response = chatModel.call(prompt);
        String text = response.getResult().getOutput().getText();
        Usage usage = response.getMetadata().getUsage();

        return new BatchResult(parse(text),
                usage.getPromptTokens() == null ? 0 : usage.getPromptTokens(),
                usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens());
    }

    /** Compact text: only the fields the model needs, description cut short to save tokens. */
    private String postsText(List<Job> batch) {
        StringBuilder sb = new StringBuilder("Posts:\n");
        for (Job j : batch) {
            String desc = j.getDescription() == null ? "" : j.getDescription();
            if (desc.length() > props.descriptionChars()) desc = desc.substring(0, props.descriptionChars());
            sb.append("\nid: ").append(j.getId())
              .append("\ntitle: ").append(j.getTitle())
              .append("\ncompany: ").append(j.getCompany())
              .append("\nlocation: ").append(j.getLocationRaw())
              .append("\ndescription: ").append(desc.replace('\n', ' '))
              .append("\n---");
        }
        return sb.toString();
    }

    /** Accepts {"tags":[...]} or a bare [...]; tolerates ```json fences. Throws if not JSON. */
    List<RawTag> parse(String text) {
        try {
            String clean = text.strip();
            if (clean.startsWith("```")) {
                clean = clean.replaceFirst("^```(json)?", "").replaceFirst("```$", "").strip();
            }
            JsonNode root = mapper.readTree(clean);
            JsonNode arr = root.isArray() ? root : root.path("tags");
            List<RawTag> out = new ArrayList<>();
            for (JsonNode t : arr) {
                JsonNode fresher = t.path("is_fresher");
                out.add(new RawTag(
                        t.path("id").asLong(-1),
                        t.path("role").asText(null),
                        t.path("city").asText(null),
                        fresher.isMissingNode() || fresher.isNull() ? null : fresher.asBoolean()));
            }
            return out;
        } catch (IOException e) {
            throw new IllegalStateException("LLM did not return valid JSON: " + e.getMessage(), e);
        }
    }
}
