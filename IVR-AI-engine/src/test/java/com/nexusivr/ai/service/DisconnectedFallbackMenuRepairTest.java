package com.nexusivr.ai.service;

import com.nexusivr.ai.ai.PromptBuilder;
import com.nexusivr.ai.dto.response.FlowValidationResponse;
import com.nexusivr.ai.model.flow.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Disconnected Fallback Menu Detection & Repair Tests")
public class DisconnectedFallbackMenuRepairTest {

    private final ModelAutoRepair autoRepair = new ModelAutoRepair();
    private final ModelFlowValidator validator = new ModelFlowValidator();

    @Test
    @DisplayName("PromptBuilder system prompt must contain AI Dynamic Routing Fallback Wiring rule & example")
    void testPromptBuilderContainsFallbackWiringRule() {
        String prompt = PromptBuilder.FLOW_GENERATOR_SYSTEM_INSTRUCTION;
        assertTrue(prompt.contains("AI DYNAMIC ROUTING & FALLBACK MENU WIRING RULE"), "Prompt must contain AI dynamic routing fallback rule");
        assertTrue(prompt.contains("nomatch"), "Prompt must show nomatch fallback element");
        assertTrue(prompt.contains("<goto next=\"#main_menu\"/>"), "Prompt must show goto next=#main_menu example");
    }

    @Test
    @DisplayName("ModelFlowValidator must flag DISCONNECTED_FALLBACK_MENU when an unlinked Menu duplicates AI routing destinations")
    void testValidatorFlagsDisconnectedFallbackMenu() {
        FlowModel model = createFlowWithDisconnectedFallbackMenu();

        FlowValidationResponse preVal = validator.validate(model);
        assertTrue(preVal.getIssues().stream().anyMatch(i -> "DISCONNECTED_FALLBACK_MENU".equals(i.getCode())),
                "Validator must flag DISCONNECTED_FALLBACK_MENU warning");
    }

    @Test
    @DisplayName("ModelAutoRepair must automatically wire AI routing node's nomatch port to disconnected fallback Menu")
    void testModelAutoRepairConnectsFallbackMenu() {
        FlowModel model = createFlowWithDisconnectedFallbackMenu();

        FlowValidationResponse preVal = validator.validate(model);
        FlowModel repaired = autoRepair.repair(model, preVal);

        FlowValidationResponse postVal = validator.validate(repaired);
        assertTrue(postVal.isValid(), "Repaired flow MUST be valid");
        assertFalse(postVal.getIssues().stream().anyMatch(i -> "DISCONNECTED_FALLBACK_MENU".equals(i.getCode())),
                "Repaired flow MUST have 0 DISCONNECTED_FALLBACK_MENU warnings");
        assertFalse(postVal.getIssues().stream().anyMatch(i -> "UNREACHABLE_NODE".equals(i.getCode())),
                "Repaired flow MUST have 0 UNREACHABLE_NODE warnings");

        boolean fallbackConnected = repaired.getConnections().stream()
                .anyMatch(c -> "ai_routing".equals(c.getSourceNodeId()) && "main_menu".equals(c.getTargetNodeId()) && "nomatch".equals(c.getSourcePort()));
        assertTrue(fallbackConnected, "ModelAutoRepair MUST add connection from ai_routing (nomatch) to main_menu");

        assertTrue(postVal.getScore() >= 90, "Repaired flow score MUST be >= 90, actual=" + postVal.getScore());
    }

    private FlowModel createFlowWithDisconnectedFallbackMenu() {
        FlowModel model = new FlowModel();

        FlowNode startNode = new FlowNode("start", FlowNodeType.START, "Start Call");
        FlowNode aiNode = new FlowNode("ai_routing", FlowNodeType.AI, "AI Voice Assistant");
        aiNode.setMenu(new FlowMenu());
        aiNode.getMenu().addChoice(new FlowChoice("passport_new", "New Passport", "new_passport_form"));
        aiNode.getMenu().addChoice(new FlowChoice("passport_renew", "Renew Passport", "renew_passport_form"));

        // Disconnected parallel DTMF Menu
        FlowNode menuNode = new FlowNode("main_menu", FlowNodeType.MENU, "Main Menu");
        menuNode.setMenu(new FlowMenu());
        menuNode.getMenu().addChoice(new FlowChoice("key1", "New Passport", "new_passport_form"));
        menuNode.getMenu().addChoice(new FlowChoice("key2", "Renew Passport", "renew_passport_form"));

        FlowNode newPassportNode = new FlowNode("new_passport_form", FlowNodeType.TRANSFER, "New Passport Form");
        newPassportNode.setTransfer(new FlowTransfer("101"));

        FlowNode renewPassportNode = new FlowNode("renew_passport_form", FlowNodeType.TRANSFER, "Renew Passport Form");
        renewPassportNode.setTransfer(new FlowTransfer("102"));

        FlowNode endNode = new FlowNode("end", FlowNodeType.END, "End Call");

        model.addNode(startNode);
        model.addNode(aiNode);
        model.addNode(menuNode);
        model.addNode(newPassportNode);
        model.addNode(renewPassportNode);
        model.addNode(endNode);

        // Wire start -> ai_routing, ai_routing choices -> target forms
        model.addConnection(new FlowConnection("c1", "start", "out", "ai_routing", "in"));
        model.addConnection(new FlowConnection("c2", "ai_routing", "passport_new", "new_passport_form", "in"));
        model.addConnection(new FlowConnection("c3", "ai_routing", "passport_renew", "renew_passport_form", "in"));
        model.addConnection(new FlowConnection("c4", "main_menu", "key1", "new_passport_form", "in"));
        model.addConnection(new FlowConnection("c5", "main_menu", "key2", "renew_passport_form", "in"));
        model.addConnection(new FlowConnection("c6", "new_passport_form", "fail", "end", "in"));
        model.addConnection(new FlowConnection("c7", "renew_passport_form", "fail", "end", "in"));

        // Note: main_menu has NO incoming connection from start or ai_routing!
        return model;
    }
}
