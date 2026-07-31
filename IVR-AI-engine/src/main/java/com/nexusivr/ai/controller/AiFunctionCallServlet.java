package com.nexusivr.ai.controller;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nexusivr.ai.ai.FunctionExecutor;
import com.nexusivr.ai.dto.common.FunctionCallDto;
import com.nexusivr.ai.dto.common.FunctionDefinitionDto;
import com.nexusivr.ai.dto.request.FunctionCallRequest;
import com.nexusivr.ai.dto.response.FunctionCallResponse;
import com.nexusivr.ai.exception.ValidationException;
import com.nexusivr.ai.service.AiService;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller servlet handling AI function calling execution requests.
 * Endpoint: POST /api/v1/ai/function-call
 */
@WebServlet(urlPatterns = {"/api/v1/ai/function-call"})
public class AiFunctionCallServlet extends BaseAiServlet {

    private final AiService aiService;

    public AiFunctionCallServlet(AiService aiService) {
        this.aiService = aiService;
    }

    public AiFunctionCallServlet() {
        this(null);
    }

    private AiService getAiService() {
        return aiService != null ? aiService : ServiceRegistry.getAiService();
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = req.getReader()) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
            String body = sb.toString();
            if (body.isBlank()) {
                throw new ValidationException("Request body is required");
            }

            JsonObject json = JsonParser.parseString(body).getAsJsonObject();

            String functionName;
            Map<String, Object> parameters = new HashMap<>();

            if (json.has("functionName")) {
                functionName = json.get("functionName").getAsString();
                if (json.has("parameters") && json.get("parameters").isJsonObject()) {
                    for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("parameters").entrySet()) {
                        parameters.put(entry.getKey(), entry.getValue().isJsonPrimitive() ?
                                entry.getValue().getAsString() : entry.getValue().toString());
                    }
                }
            } else if (json.has("userMessage")) {
                FunctionCallRequest request = gson.fromJson(json, FunctionCallRequest.class);
                FunctionExecutor executor = getAiService().getFunctionExecutor();
                functionName = resolveFunctionName(request, executor);
            } else {
                throw new ValidationException("Either functionName or userMessage is required");
            }

            String callId = "call-" + System.currentTimeMillis();
            String resultJson = getAiService().executeFunction(functionName, parameters);

            FunctionCallDto functionCallDto = new FunctionCallDto(callId, functionName, Map.of("result", resultJson));
            FunctionCallResponse response = new FunctionCallResponse(List.of(functionCallDto), "Function executed successfully");

            sendJsonResponse(resp, HttpServletResponse.SC_OK, response);
        } catch (Exception e) {
            handleError(resp, e);
        }
    }

    private String resolveFunctionName(FunctionCallRequest request, FunctionExecutor executor) {
        List<FunctionDefinitionDto> available = request.getAvailableFunctions();
        if (available != null && !available.isEmpty()) {
            for (FunctionDefinitionDto fn : available) {
                if (fn.getName() != null && executor.hasFunction(fn.getName())) {
                    return fn.getName();
                }
            }
        }

        String msg = request.getUserMessage().toLowerCase();
        if (msg.contains("transfer") || msg.contains("agent") || msg.contains("speak")) {
            return "transferToAgent";
        } else if (msg.contains("balance") || msg.contains("account")) {
            return "checkCustomerBalance";
        } else if (msg.contains("ticket") || msg.contains("issue") || msg.contains("problem")) {
            return "createSupportTicket";
        } else if (msg.contains("appointment") || msg.contains("book") || msg.contains("schedule")) {
            return "bookAppointment";
        }
        return "transferToAgent";
    }
}
