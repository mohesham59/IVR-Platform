package com.nexusivr.ai.controller;

import com.nexusivr.ai.ai.ProviderManager;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller servlet serving dynamic providers and model lists to the frontend.
 */
@WebServlet(urlPatterns = "/api/v1/ai/providers")
public class AiProviderListServlet extends BaseAiServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            ProviderManager providerManager = ServiceRegistry.getProviderManager();
            Map<String, List<String>> allProviders = providerManager.getSupportedProvidersAndModels();

            List<Map<String, Object>> providerList = new ArrayList<>();
            for (Map.Entry<String, List<String>> entry : allProviders.entrySet()) {
                String providerName = entry.getKey();
                if ("mock".equalsIgnoreCase(providerName)) {
                    continue;
                }
                List<String> models = entry.getValue();
                String model = models != null && !models.isEmpty() ? models.getFirst() : "";

                boolean enabled = providerManager.isProviderAvailable(providerName);

                Map<String, Object> providerMap = new LinkedHashMap<>();
                providerMap.put("name", providerName);
                providerMap.put("enabled", enabled);
                providerMap.put("model", model);
                providerList.add(providerMap);
            }

            sendJsonResponse(resp, HttpServletResponse.SC_OK, Map.of("success", true, "providers", providerList));
        } catch (Exception e) {
            handleError(resp, e);
        }
    }
}
