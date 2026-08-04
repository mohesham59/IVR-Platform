package com.nexusivr.ai.ai;

import com.nexusivr.ai.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Offline-compatible simulated Mock LLM client.
 * Returns valid structured JSON configurations and conversational chat turns locally.
 */
public class MockLlmClient implements LlmClient {

    private static final Logger logger = LoggerFactory.getLogger(MockLlmClient.class);

    private final String model;

    public MockLlmClient() {
        this("mock-model");
    }

    public MockLlmClient(String model) {
        this.model = model != null ? model : "mock-model";
        logger.info("MockLlmClient initialized. Model: {}", this.model);
    }

    @Override
    public String getProviderName() {
        return "mock";
    }

    @Override
    public String getModelName() {
        return model;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public AiResponse generateResponse(String prompt, List<Message> history) {
        return generateResponse(null, prompt, history);
    }

    @Override
    public AiResponse generateResponse(String systemPrompt, String userPrompt, List<Message> history) {
        logger.info("MockLlmClient: Simulating chat response for prompt: {}", userPrompt);
        String reply = "Simulated Response (Mock Provider): I have processed your request '" + userPrompt + "'. The current flow structure has been analyzed.";
        return new AiResponse(reply, model, 15, 20, false);
    }

    @Override
    public AiResponse generateStructuredResponse(String prompt, List<Message> history) {
        return generateStructuredResponse(null, prompt, history);
    }

    @Override
    public AiResponse generateStructuredResponse(String systemPrompt, String userPrompt, List<Message> history) {
        logger.info("MockLlmClient: Simulating structured JSON flow for prompt: {}", userPrompt);
        String clean = userPrompt != null ? userPrompt.toLowerCase() : "";

        String json;
        if (clean.contains("bank") || clean.contains("loan") || clean.contains("account") || clean.contains("finance")) {
            json = getBankFlowJson();
        } else if (clean.contains("hospital") || clean.contains("clinic") || clean.contains("medical") || clean.contains("doctor")) {
            json = getHospitalFlowJson();
        } else {
            json = getGenericFlowJson();
        }

        return new AiResponse(json, model, 25, 120, false);
    }

    private String getBankFlowJson() {
        return "{" +
                "\"name\": \"Bank Customer Service IVR\"," +
                "\"description\": \"Simulated bank routing workflow\"," +
                "\"nodes\": [" +
                "  {\"id\": \"n1\", \"type\": \"start\", \"title\": \"Incoming Call\", \"subtitle\": \"Standard entry\"}," +
                "  {\"id\": \"n2\", \"type\": \"greeting\", \"title\": \"Welcome Greeting\", \"subtitle\": \"Play welcome prompt\"}," +
                "  {\"id\": \"n3\", \"type\": \"dtmf_menu\", \"title\": \"Account Services Menu\", \"subtitle\": \"1:Balance 2:Loans 3:Hotline 0:Agent\"}," +
                "  {\"id\": \"n4\", \"type\": \"api\", \"title\": \"Check Account Balance\", \"subtitle\": \"Retrieve customer funds\"}," +
                "  {\"id\": \"n5\", \"type\": \"queue\", \"title\": \"Loans Support Queue\", \"subtitle\": \"Wait for credit agent\"}," +
                "  {\"id\": \"n6\", \"type\": \"transfer\", \"title\": \"Transfer to Banking Representative\", \"subtitle\": \"Direct transfer hotline\"}," +
                "  {\"id\": \"n7\", \"type\": \"end\", \"title\": \"End Call\", \"subtitle\": \"Hang up connection\"}" +
                "]," +
                "\"edges\": [" +
                "  {\"id\": \"e1\", \"sourceId\": \"n1\", \"sourcePort\": \"out\", \"targetId\": \"n2\", \"targetPort\": \"in\"}," +
                "  {\"id\": \"e2\", \"sourceId\": \"n2\", \"sourcePort\": \"out\", \"targetId\": \"n3\", \"targetPort\": \"in\"}," +
                "  {\"id\": \"e3\", \"sourceId\": \"n3\", \"sourcePort\": \"key1\", \"targetId\": \"n4\", \"targetPort\": \"in\"}," +
                "  {\"id\": \"e4\", \"sourceId\": \"n3\", \"sourcePort\": \"key2\", \"targetId\": \"n5\", \"targetPort\": \"in\"}," +
                "  {\"id\": \"e5\", \"sourceId\": \"n3\", \"sourcePort\": \"key3\", \"targetId\": \"n6\", \"targetPort\": \"in\"}," +
                "  {\"id\": \"e6\", \"sourceId\": \"n4\", \"sourcePort\": \"success\", \"targetId\": \"n7\", \"targetPort\": \"in\"}," +
                "  {\"id\": \"e7\", \"sourceId\": \"n5\", \"sourcePort\": \"answered\", \"targetId\": \"n7\", \"targetPort\": \"in\"}," +
                "  {\"id\": \"e8\", \"sourceId\": \"n6\", \"sourcePort\": \"success\", \"targetId\": \"n7\", \"targetPort\": \"in\"}" +
                "]" +
                "}";
    }

    private String getHospitalFlowJson() {
        return "{" +
                "\"name\": \"Hospital Main IVR\"," +
                "\"description\": \"Simulated clinic routing workflow\"," +
                "\"nodes\": [" +
                "  {\"id\": \"n1\", \"type\": \"start\", \"title\": \"Incoming Call\", \"subtitle\": \"Clinic line\"}," +
                "  {\"id\": \"n2\", \"type\": \"greeting\", \"title\": \"Welcome Greeting\", \"subtitle\": \"Triage Greeting\"}," +
                "  {\"id\": \"n3\", \"type\": \"dtmf_menu\", \"title\": \"Department Selector\", \"subtitle\": \"1:Appts 2:Billing 3:Pharmacy 0:Emergency\"}," +
                "  {\"id\": \"n4\", \"type\": \"queue\", \"title\": \"Appointments Queue\", \"subtitle\": \"Wait for scheduling agent\"}," +
                "  {\"id\": \"n5\", \"type\": \"transfer\", \"title\": \"Billing Specialist\", \"subtitle\": \"Direct transfer line\"}," +
                "  {\"id\": \"n6\", \"type\": \"transfer\", \"title\": \"Emergency Hotline\", \"subtitle\": \"Urgent routing\"}," +
                "  {\"id\": \"n7\", \"type\": \"end\", \"title\": \"End Call\", \"subtitle\": \"Graceful disconnect\"}" +
                "]," +
                "\"edges\": [" +
                "  {\"id\": \"e1\", \"sourceId\": \"n1\", \"sourcePort\": \"out\", \"targetId\": \"n2\", \"targetPort\": \"in\"}," +
                "  {\"id\": \"e2\", \"sourceId\": \"n2\", \"sourcePort\": \"out\", \"targetId\": \"n3\", \"targetPort\": \"in\"}," +
                "  {\"id\": \"e3\", \"sourceId\": \"n3\", \"sourcePort\": \"key1\", \"targetId\": \"n4\", \"targetPort\": \"in\"}," +
                "  {\"id\": \"e4\", \"sourceId\": \"n3\", \"sourcePort\": \"key2\", \"targetId\": \"n5\", \"targetPort\": \"in\"}," +
                "  {\"id\": \"e5\", \"sourceId\": \"n3\", \"sourcePort\": \"key0\", \"targetId\": \"n6\", \"targetPort\": \"in\"}," +
                "  {\"id\": \"e6\", \"sourceId\": \"n4\", \"sourcePort\": \"answered\", \"targetId\": \"n7\", \"targetPort\": \"in\"}," +
                "  {\"id\": \"e7\", \"sourceId\": \"n5\", \"sourcePort\": \"success\", \"targetId\": \"n7\", \"targetPort\": \"in\"}," +
                "  {\"id\": \"e8\", \"sourceId\": \"n6\", \"sourcePort\": \"success\", \"targetId\": \"n7\", \"targetPort\": \"in\"}" +
                "]" +
                "}";
    }

    private String getGenericFlowJson() {
        return "{" +
                "\"name\": \"Acme Support IVR\"," +
                "\"description\": \"Simulated general business workflow\"," +
                "\"nodes\": [" +
                "  {\"id\": \"n1\", \"type\": \"start\", \"title\": \"Incoming Call\", \"subtitle\": \"Main Line\"}," +
                "  {\"id\": \"n2\", \"type\": \"greeting\", \"title\": \"Welcome Greeting\", \"subtitle\": \"Service Welcome\"}," +
                "  {\"id\": \"n3\", \"type\": \"dtmf_menu\", \"title\": \"Main IVR Menu\", \"subtitle\": \"1:Support 2:Sales 0:Operator\"}," +
                "  {\"id\": \"n4\", \"type\": \"queue\", \"title\": \"Support Queue\", \"subtitle\": \"Wait for technician\"}," +
                "  {\"id\": \"n5\", \"type\": \"transfer\", \"title\": \"Sales Representative\", \"subtitle\": \"Sales transfer\"}," +
                "  {\"id\": \"n6\", \"type\": \"end\", \"title\": \"End Call\", \"subtitle\": \"Disconnect line\"}" +
                "]," +
                "\"edges\": [" +
                "  {\"id\": \"e1\", \"sourceId\": \"n1\", \"sourcePort\": \"out\", \"targetId\": \"n2\", \"targetPort\": \"in\"}," +
                "  {\"id\": \"e2\", \"sourceId\": \"n2\", \"sourcePort\": \"out\", \"targetId\": \"n3\", \"targetPort\": \"in\"}," +
                "  {\"id\": \"e3\", \"sourceId\": \"n3\", \"sourcePort\": \"key1\", \"targetId\": \"n4\", \"targetPort\": \"in\"}," +
                "  {\"id\": \"e4\", \"sourceId\": \"n3\", \"sourcePort\": \"key2\", \"targetId\": \"n5\", \"targetPort\": \"in\"}," +
                "  {\"id\": \"e5\", \"sourceId\": \"n4\", \"sourcePort\": \"answered\", \"targetId\": \"n6\", \"targetPort\": \"in\"}," +
                "  {\"id\": \"e6\", \"sourceId\": \"n5\", \"sourcePort\": \"success\", \"targetId\": \"n6\", \"targetPort\": \"in\"}" +
                "]" +
                "}";
    }
}
