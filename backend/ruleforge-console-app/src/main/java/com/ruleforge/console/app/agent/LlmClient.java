package com.ruleforge.console.app.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruleforge.console.app.agent.model.AgentModels.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

/**
 * LLM HTTP 客户端 — OpenAI 兼容格式的 Chat Completions API。
 *
 * <p>支持：
 * <ul>
 *   <li>同步调用（非流式）</li>
 *   <li>SSE 流式调用</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmClient {

    private final AgentConfigService configService;
    private final ObjectMapper objectMapper;

    /**
     * 同步调用 LLM（非流式）。
     */
    public ChatCompletionResponse chat(List<ChatCompletionRequest.MessageItem> messages,
                                       List<ToolDef> tools) {
        RestTemplate restTemplate = createRestTemplate();

        ChatCompletionRequest request = buildRequest(messages, tools, false);
        String url = configService.get("llm.base_url") + "/chat/completions";

        HttpHeaders headers = buildHeaders();
        HttpEntity<ChatCompletionRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<ChatCompletionResponse> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, ChatCompletionResponse.class);

        return response.getBody();
    }

    /**
     * 流式调用 LLM，逐 token 回调。
     */
    public void chatStream(List<ChatCompletionRequest.MessageItem> messages,
                           List<ToolDef> tools,
                           Consumer<String> onToken,
                           Consumer<ChatCompletionResponse> onDone) {
        try {
            ChatCompletionRequest request = buildRequest(messages, tools, true);
            String url = configService.get("llm.base_url") + "/chat/completions";
            String jsonBody = objectMapper.writeValueAsString(request);

            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(120_000);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + configService.get("llm.api_key"));
            conn.setRequestProperty("Accept", "text/event-stream");

            conn.getOutputStream().write(jsonBody.getBytes(StandardCharsets.UTF_8));

            StringBuilder contentBuilder = new StringBuilder();
            String finishReason = null;
            String responseId = null;
            String responseModel = null;
            java.util.Map<String, ToolCallAccumulator> toolCallAccumulators = new java.util.LinkedHashMap<>();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        String data = line.substring(6).trim();
                        if ("[DONE]".equals(data)) break;
                        JsonNode chunk = objectMapper.readTree(data);
                        responseId = chunk.path("id").asText(responseId);
                        responseModel = chunk.path("model").asText(responseModel);

                        JsonNode choices = chunk.path("choices");
                        if (choices.isArray() && !choices.isEmpty()) {
                            JsonNode choice = choices.get(0);
                            finishReason = choice.path("finish_reason").asText(finishReason);
                            JsonNode delta = choice.path("delta");

                            String content = delta.path("content").asText("");
                            if (!content.isEmpty() && !"null".equals(content)) {
                                contentBuilder.append(content);
                                onToken.accept(content);
                            }

                            JsonNode toolCallsNode = delta.path("tool_calls");
                            if (toolCallsNode.isArray()) {
                                for (JsonNode tc : toolCallsNode) {
                                    String tcId = tc.path("id").asText("");
                                    int tcIndex = tc.path("index").asInt();
                                    String key = tcId.isEmpty() ? String.valueOf(tcIndex) : tcId;
                                    ToolCallAccumulator acc = toolCallAccumulators.computeIfAbsent(
                                            key, k -> new ToolCallAccumulator());
                                    if (!tcId.isEmpty()) acc.id = tcId;
                                    JsonNode fn = tc.path("function");
                                    if (fn.has("name")) acc.name = fn.get("name").asText();
                                    if (fn.has("arguments")) acc.arguments += fn.get("arguments").asText();
                                }
                            }
                        }
                    }
                }
            }

            ChatCompletionResponse fullResponse = new ChatCompletionResponse();
            fullResponse.setId(responseId);
            fullResponse.setModel(responseModel);

            ChatCompletionResponse.ChoiceItem choice = new ChatCompletionResponse.ChoiceItem();
            choice.setFinishReason(finishReason);

            ChatCompletionRequest.MessageItem msg = new ChatCompletionRequest.MessageItem();
            msg.setRole("assistant");
            msg.setContent(contentBuilder.toString());

            if (!toolCallAccumulators.isEmpty()) {
                List<ChatCompletionRequest.ToolCallItem> toolCalls = new java.util.ArrayList<>();
                for (ToolCallAccumulator acc : toolCallAccumulators.values()) {
                    ChatCompletionRequest.ToolCallItem tci = new ChatCompletionRequest.ToolCallItem();
                    tci.setId(acc.id);
                    tci.setType("function");
                    ChatCompletionRequest.FunctionCall fc = new ChatCompletionRequest.FunctionCall();
                    fc.setName(acc.name);
                    fc.setArguments(acc.arguments);
                    tci.setFunction(fc);
                    toolCalls.add(tci);
                }
                msg.setToolCalls(toolCalls);
            }

            choice.setMessage(msg);
            fullResponse.setChoices(List.of(choice));
            onDone.accept(fullResponse);

        } catch (Exception e) {
            log.error("LLM streaming call failed", e);
            throw new RuntimeException("LLM 调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 测试 LLM 连接
     */
    public boolean testConnection() {
        try {
            RestTemplate restTemplate = createRestTemplate();
            String baseUrl = configService.get("llm.base_url");
            String url = baseUrl.replaceAll("/v1$", "") + "/v1/models";
            HttpHeaders headers = buildHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("LLM connection test failed: {}", e.getMessage());
            return false;
        }
    }

    // ===== internal =====

    private RestTemplate createRestTemplate() {
        return new RestTemplate();
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(configService.get("llm.api_key"));
        return headers;
    }

    private ChatCompletionRequest buildRequest(List<ChatCompletionRequest.MessageItem> messages,
                                               List<ToolDef> tools, boolean stream) {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel(configService.get("llm.model"));
        request.setMessages(messages);
        request.setTools(tools != null && !tools.isEmpty() ? tools : null);
        request.setTemperature(Double.parseDouble(configService.get("llm.temperature")));
        request.setMaxTokens(Integer.parseInt(configService.get("llm.max_tokens")));
        request.setStream(stream);
        return request;
    }

    /** 累加 tool_call 增量数据 */
    private static class ToolCallAccumulator {
        String id = "";
        String name = "";
        String arguments = "";
    }
}
