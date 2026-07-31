package com.nexusivr.ai.ai;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;

/**
 * Handles function calling execution for AI actions.
 * Registers mock handlers for telephony and customer service operations.
 */
public class FunctionExecutor {

    private static final Logger logger = LoggerFactory.getLogger(FunctionExecutor.class);
    private final Map<String, Function<Map<String, Object>, String>> functionRegistry = new HashMap<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public FunctionExecutor() {
        registerDefaultFunctions();
    }

    private void registerDefaultFunctions() {
        // 1. transferToAgent()
        registerFunction("transferToAgent", params -> {
            String department = Objects.toString(params.getOrDefault("department", "general_support"), "general_support");
            String priority = Objects.toString(params.getOrDefault("priority", "normal"), "normal");
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "SUCCESS");
            result.put("function", "transferToAgent");
            result.put("department", department);
            result.put("priority", priority);
            result.put("targetExtension", "1004");
            result.put("message", "Call successfully queued for transfer to " + department + " (priority: " + priority + ")");
            return gson.toJson(result);
        });

        // 2. checkCustomerBalance()
        registerFunction("checkCustomerBalance", params -> {
            String customerId = Objects.toString(params.getOrDefault("customerId", "CUST-1001"), "CUST-1001");
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "SUCCESS");
            result.put("function", "checkCustomerBalance");
            result.put("customerId", customerId);
            result.put("balance", 250.75);
            result.put("currency", "USD");
            result.put("statusMessage", "Account balance retrieved successfully");
            return gson.toJson(result);
        });

        // 3. createSupportTicket()
        registerFunction("createSupportTicket", params -> {
            String customerId = Objects.toString(params.getOrDefault("customerId", "CUST-1001"), "CUST-1001");
            String issue = Objects.toString(params.getOrDefault("issue", "General Inquiry"), "General Inquiry");
            String category = Objects.toString(params.getOrDefault("category", "SUPPORT"), "SUPPORT");
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "SUCCESS");
            result.put("function", "createSupportTicket");
            result.put("ticketId", "TICK-" + (100000 + new Random().nextInt(900000)));
            result.put("customerId", customerId);
            result.put("category", category);
            result.put("issue", issue);
            result.put("ticketStatus", "OPEN");
            result.put("message", "Support ticket created for issue: " + issue);
            return gson.toJson(result);
        });

        // 4. bookAppointment()
        registerFunction("bookAppointment", params -> {
            String customerId = Objects.toString(params.getOrDefault("customerId", "CUST-1001"), "CUST-1001");
            String date = Objects.toString(params.getOrDefault("date", "2026-08-01"), "2026-08-01");
            String timeSlot = Objects.toString(params.getOrDefault("timeSlot", "10:00 AM"), "10:00 AM");
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "SUCCESS");
            result.put("function", "bookAppointment");
            result.put("appointmentId", "APT-" + (10000 + new Random().nextInt(90000)));
            result.put("customerId", customerId);
            result.put("date", date);
            result.put("timeSlot", timeSlot);
            result.put("message", "Appointment confirmed for " + date + " at " + timeSlot);
            return gson.toJson(result);
        });
    }

    /**
     * Registers a function handler with a given function name.
     */
    public void registerFunction(String name, Function<Map<String, Object>, String> handler) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Function name cannot be null or empty");
        }
        functionRegistry.put(name, Objects.requireNonNull(handler, "Function handler cannot be null"));
        logger.debug("Registered function handler: {}", name);
    }

    /**
     * Executes a registered mock function with the supplied arguments.
     *
     * @param functionName name of the registered function
     * @param parameters   parameters map
     * @return JSON result string
     * @throws AiException if the function is not registered
     */
    public String executeFunction(String functionName, Map<String, Object> parameters) {
        if (functionName == null || !functionRegistry.containsKey(functionName)) {
            logger.error("Attempted to execute unregistered function: {}", functionName);
            throw new AiException("Unregistered function call: " + functionName);
        }

        Map<String, Object> safeParams = parameters != null ? parameters : Collections.emptyMap();
        try {
            logger.info("Executing function '{}' with params: {}", functionName, safeParams);
            return functionRegistry.get(functionName).apply(safeParams);
        } catch (Exception e) {
            logger.error("Error executing function {}", functionName, e);
            throw new AiException("Error executing function " + functionName + ": " + e.getMessage(), e);
        }
    }

    public boolean hasFunction(String functionName) {
        return functionName != null && functionRegistry.containsKey(functionName);
    }

    public Set<String> getRegisteredFunctions() {
        return Collections.unmodifiableSet(functionRegistry.keySet());
    }
}
