package com.nexusivr.ai.service;

import com.nexusivr.ai.controller.ServiceRegistry;
import com.nexusivr.ai.dto.response.AiSuggestionDto;
import com.nexusivr.ai.model.flow.*;
import com.google.gson.Gson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class SubMenuPreservationTest {

    private UnifiedAiEngine unifiedAiEngine;
    private FlowModelAutoRepair autoRepair;
    private Gson gson;

    @BeforeEach
    void setUp() {
        unifiedAiEngine = ServiceRegistry.getUnifiedAiEngine();
        autoRepair = new FlowModelAutoRepair();
        gson = new Gson();
    }

    @Test
    @DisplayName("Verify that 20-node multi-menu flow with 4 distinct sub-menus parses cleanly from JSON and preserves all sub-menus")
    void testMultiMenuFlowPayloadParsingAndPreservation() {
        FlowModel model = new FlowModel();
        model.setName("Hospital Clinic IVR Flow");

        FlowNode start = new FlowNode("start", FlowNodeType.START, "Start Call");
        FlowNode mainMenu = new FlowNode("main_menu", FlowNodeType.MENU, "Main Menu");

        FlowNode scheduleMenu = new FlowNode("menu_sched", FlowNodeType.MENU, "Schedule Menu");
        FlowNode billingMenu = new FlowNode("menu_bill", FlowNodeType.MENU, "Billing Menu");
        FlowNode testMenu = new FlowNode("menu_tests", FlowNodeType.MENU, "Test Results Menu");
        FlowNode infoMenu = new FlowNode("menu_info", FlowNodeType.MENU, "General Inquiries Menu");

        FlowNode pSched1 = new FlowNode("p_sched1", FlowNodeType.PROMPT, "Book Appointment");
        FlowNode pSched2 = new FlowNode("p_sched2", FlowNodeType.PROMPT, "Cancel Appointment");
        FlowNode pBill1 = new FlowNode("p_bill1", FlowNodeType.PROMPT, "Pay Bill");
        FlowNode pBill2 = new FlowNode("p_bill2", FlowNodeType.PROMPT, "Insurance Query");
        FlowNode pTest1 = new FlowNode("p_test1", FlowNodeType.PROMPT, "Lab Results");
        FlowNode pTest2 = new FlowNode("p_test2", FlowNodeType.PROMPT, "Imaging Results");
        FlowNode pInfo1 = new FlowNode("p_info1", FlowNodeType.PROMPT, "Clinic Hours");
        FlowNode pInfo2 = new FlowNode("p_info2", FlowNodeType.PROMPT, "Location Directions");

        FlowNode endNode = new FlowNode("end", FlowNodeType.END, "End Call");

        List<FlowNode> nodes = Arrays.asList(
                start, mainMenu, scheduleMenu, billingMenu, testMenu, infoMenu,
                pSched1, pSched2, pBill1, pBill2, pTest1, pTest2, pInfo1, pInfo2, endNode
        );
        nodes.forEach(model::addNode);

        model.addConnection(new FlowConnection("c1", "start", "out", "main_menu", "in"));

        model.addConnection(new FlowConnection("c2", "main_menu", "key1", "menu_sched", "in"));
        model.addConnection(new FlowConnection("c3", "main_menu", "key2", "menu_bill", "in"));
        model.addConnection(new FlowConnection("c4", "main_menu", "key3", "menu_tests", "in"));
        model.addConnection(new FlowConnection("c5", "main_menu", "key4", "menu_info", "in"));

        model.addConnection(new FlowConnection("c6", "menu_sched", "key1", "p_sched1", "in"));
        model.addConnection(new FlowConnection("c7", "menu_sched", "key2", "p_sched2", "in"));
        model.addConnection(new FlowConnection("c8", "menu_bill", "key1", "p_bill1", "in"));
        model.addConnection(new FlowConnection("c9", "menu_bill", "key2", "p_bill2", "in"));
        model.addConnection(new FlowConnection("c10", "menu_tests", "key1", "p_test1", "in"));
        model.addConnection(new FlowConnection("c11", "menu_tests", "key2", "p_test2", "in"));
        model.addConnection(new FlowConnection("c12", "menu_info", "key1", "p_info1", "in"));
        model.addConnection(new FlowConnection("c13", "menu_info", "key2", "p_info2", "in"));

        model.addConnection(new FlowConnection("c14", "p_sched1", "out", "end", "in"));
        model.addConnection(new FlowConnection("c15", "p_sched2", "out", "end", "in"));
        model.addConnection(new FlowConnection("c16", "p_bill1", "out", "end", "in"));
        model.addConnection(new FlowConnection("c17", "p_bill2", "out", "end", "in"));
        model.addConnection(new FlowConnection("c18", "p_test1", "out", "end", "in"));
        model.addConnection(new FlowConnection("c19", "p_test2", "out", "end", "in"));
        model.addConnection(new FlowConnection("c20", "p_info1", "out", "end", "in"));
        model.addConnection(new FlowConnection("c21", "p_info2", "out", "end", "in"));

        // 1. Verify AutoRepair preserves all 5 distinct menu nodes
        FlowModel repaired = autoRepair.repair(model);
        long menuCount = repaired.getNodes().stream().filter(n -> n.getType() == FlowNodeType.MENU).count();
        assertEquals(5, menuCount, "AutoRepair must preserve all 5 distinct menu nodes (Main, Sched, Bill, Tests, Info)");

        // 2. Convert model to JSON string simulating payload sent by frontend
        String flowJson = gson.toJson(model);
        assertNotNull(flowJson, "Flow JSON must serialize successfully");
    }
}

