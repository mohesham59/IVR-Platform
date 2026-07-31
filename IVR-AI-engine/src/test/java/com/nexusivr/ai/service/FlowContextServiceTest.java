package com.nexusivr.ai.service;

import com.nexusivr.ai.model.flow.FlowModel;
import com.nexusivr.ai.model.flow.FlowNode;
import com.nexusivr.ai.model.flow.FlowNodeType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FlowContextService Unit Tests")
public class FlowContextServiceTest {

    private FlowContextService flowContextService;

    @BeforeEach
    void setUp() {
        flowContextService = new FlowContextService();
    }

    @AfterEach
    void tearDown() {
        SessionMemoryStore.clear(null);
    }

    @Test
    @DisplayName("Should successfully save active flow context as FlowModel")
    void testSaveActiveFlow() {
        UUID sessionId = UUID.randomUUID();
        FlowModel initialFlow = buildMockFlowModel("flow-1", 3);

        flowContextService.saveActiveFlow(sessionId, initialFlow);

        String rendered = flowContextService.getActiveFlow(sessionId);
        assertNotNull(rendered);
        assertTrue(rendered.contains("flow-1"));
        assertTrue(SessionMemoryStore.hasFlow(sessionId));
        assertEquals(3, SessionMemoryStore.get(sessionId).getNodeCount());
    }

    @Test
    @DisplayName("Should update flow context and verify new node counts")
    void testUpdateFlowContext() {
        UUID sessionId = UUID.randomUUID();
        FlowModel initialFlow = buildMockFlowModel("flow-1", 3);
        FlowModel updatedFlow = buildMockFlowModel("flow-2", 5);

        flowContextService.saveActiveFlow(sessionId, initialFlow);
        flowContextService.updateFlowContext(sessionId, updatedFlow);

        String rendered = flowContextService.getActiveFlow(sessionId);
        assertNotNull(rendered);
        assertTrue(rendered.contains("flow-2"));
        assertEquals(5, SessionMemoryStore.get(sessionId).getNodeCount());
    }

    @Test
    @DisplayName("Should clear active flow context completely when clearOutdatedFlows is called")
    void testClearOutdatedFlows() {
        UUID sessionId = UUID.randomUUID();
        FlowModel flow = buildMockFlowModel("flow-123", 4);

        flowContextService.saveActiveFlow(sessionId, flow);
        assertTrue(SessionMemoryStore.hasFlow(sessionId));

        flowContextService.clearOutdatedFlows(sessionId);
        assertNull(flowContextService.getActiveFlow(sessionId));
        assertFalse(SessionMemoryStore.hasFlow(sessionId));
    }

    @Test
    @DisplayName("Should handle invalid input gracefully without throwing exceptions")
    void testInvalidInputSafety() {
        UUID sessionId = UUID.randomUUID();
        assertDoesNotThrow(() -> flowContextService.saveActiveFlow(sessionId, (String) null));
        assertDoesNotThrow(() -> flowContextService.saveActiveFlow(sessionId, ""));
        assertFalse(SessionMemoryStore.hasFlow(sessionId));
    }

    @Test
    @DisplayName("Should convert VoiceXML string to FlowModel and store it")
    void testSaveActiveFlowFromVxml() {
        UUID sessionId = UUID.randomUUID();
        String vxml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <vxml version="2.1" xmlns="http://www.w3.org/2001/vxml">
                  <form id="start">
                    <block>
                      <prompt>Welcome to our service</prompt>
                      <goto next="#menu"/>
                    </block>
                  </form>
                  <form id="menu">
                    <menu>
                      <prompt>Press 1 for sales</prompt>
                      <choice accept="digits 1" next="#sales"/>
                    </menu>
                  </form>
                  <form id="sales">
                    <transfer dest="1234"/>
                  </form>
                </vxml>
                """;

        flowContextService.saveActiveFlow(sessionId, vxml);
        assertTrue(SessionMemoryStore.hasFlow(sessionId));
        assertTrue(SessionMemoryStore.get(sessionId).getNodeCount() > 0);
    }

    @Test
    @DisplayName("Should convert React Flow JSON string to FlowModel and store it")
    void testSaveActiveFlowFromJson() {
        UUID sessionId = UUID.randomUUID();
        String json = """
                {"id":"flow-json","nodes":[{"id":"n1","type":"start","title":"Start"},{"id":"n2","type":"prompt","title":"Welcome"}],"edges":[{"id":"e1","source":"n1","target":"n2"}]}
                """;

        flowContextService.saveActiveFlow(sessionId, json);
        assertTrue(SessionMemoryStore.hasFlow(sessionId));
        assertEquals(2, SessionMemoryStore.get(sessionId).getNodeCount());
        assertEquals(1, SessionMemoryStore.get(sessionId).getEdgeCount());
    }

    @Test
    @DisplayName("Should convert hospital JSON flow with departments into FlowModel")
    void testConvertHospitalJsonFlow() {
        String hospitalJson = """
                {"name":"Hospital IVR","nodes":[
                  {"id":"n1","type":"start","title":"Start"},
                  {"id":"n2","type":"greeting","title":"Welcome"},
                  {"id":"n3","type":"dtmf_menu","title":"Department Menu","ports":[
                    {"id":"key1","label":"Appointments"},
                    {"id":"key2","label":"Pharmacy"},
                    {"id":"key3","label":"Billing"},
                    {"id":"key4","label":"Triage"}
                  ]},
                  {"id":"n4","type":"queue","title":"Appointments Queue"},
                  {"id":"n5","type":"end","title":"End Call"}
                ],"edges":[
                  {"id":"e1","sourceId":"n1","sourcePort":"out","targetId":"n2","targetPort":"in"},
                  {"id":"e2","sourceId":"n2","sourcePort":"out","targetId":"n3","targetPort":"in"},
                  {"id":"e3","sourceId":"n3","sourcePort":"key1","targetId":"n4","targetPort":"in"},
                  {"id":"e4","sourceId":"n3","sourcePort":"key2","targetId":"n5","targetPort":"in"}
                ]}
                """;

        FlowModel model = FlowContextService.convertJsonToModel(hospitalJson);
        assertNotNull(model);
        assertEquals("Hospital IVR", model.getName());
        assertEquals(5, model.getNodes().size());
        assertEquals(4, model.getConnections().size());
        assertTrue(model.getNodes().stream().anyMatch(n -> n.getTitle().contains("Department")));
        assertTrue(model.getNodes().stream().anyMatch(n -> n.getTitle().contains("Appointments Queue")));

        FlowNode menuNode = model.getNodes().stream()
                .filter(n -> "Department Menu".equals(n.getTitle()))
                .findFirst()
                .orElse(null);
        assertNotNull(menuNode);
        assertNotNull(menuNode.getMenu());
        assertEquals(4, menuNode.getMenu().getChoices().size());
        assertTrue(menuNode.getMenu().getChoices().stream().anyMatch(c -> c.getLabel().contains("Appointments")));
        assertTrue(menuNode.getMenu().getChoices().stream().anyMatch(c -> c.getLabel().contains("Pharmacy")));
        assertTrue(menuNode.getMenu().getChoices().stream().anyMatch(c -> c.getLabel().contains("Billing")));
        assertTrue(menuNode.getMenu().getChoices().stream().anyMatch(c -> c.getLabel().contains("Triage")));
    }

    @Test
    @DisplayName("Should convert telecom JSON flow into FlowModel")
    void testConvertTelecomJsonFlow() {
        String telecomJson = """
                {"name":"Telecom IVR","nodes":[
                  {"id":"n1","type":"start","title":"Start"},
                  {"id":"n2","type":"dtmf_menu","title":"Telecom Menu","ports":[
                    {"id":"key1","label":"Billing"},
                    {"id":"key2","label":"Roaming"},
                    {"id":"key3","label":"SIM Support"},
                    {"id":"key4","label":"Broadband"}
                  ]},
                  {"id":"n3","type":"end","title":"End Call"}
                ],"edges":[
                  {"id":"e1","sourceId":"n1","sourcePort":"out","targetId":"n2","targetPort":"in"},
                  {"id":"e2","sourceId":"n2","sourcePort":"key1","targetId":"n3","targetPort":"in"}
                ]}
                """;

        FlowModel model = FlowContextService.convertJsonToModel(telecomJson);
        assertNotNull(model);
        assertEquals("Telecom IVR", model.getName());
        assertEquals(3, model.getNodes().size());
        assertTrue(model.getNodes().stream().anyMatch(n -> n.getTitle().contains("Telecom Menu")));

        FlowNode menuNode = model.getNodes().stream()
                .filter(n -> "Telecom Menu".equals(n.getTitle()))
                .findFirst()
                .orElse(null);
        assertNotNull(menuNode);
        assertNotNull(menuNode.getMenu());
        assertEquals(4, menuNode.getMenu().getChoices().size());
        assertTrue(menuNode.getMenu().getChoices().stream().anyMatch(c -> c.getLabel().contains("Billing")));
        assertTrue(menuNode.getMenu().getChoices().stream().anyMatch(c -> c.getLabel().contains("Roaming")));
        assertTrue(menuNode.getMenu().getChoices().stream().anyMatch(c -> c.getLabel().contains("SIM Support")));
        assertTrue(menuNode.getMenu().getChoices().stream().anyMatch(c -> c.getLabel().contains("Broadband")));
    }

    @Test
    @DisplayName("Should parse nested healthcare JSON flow with 15 nodes via nested parser")
    void testConvertNestedHealthcareJsonFlow() {
        String healthcareJson = """
                {"name":"Hospital IVR","flow":{
                  "nodes":[
                    {"id":"start","type":"start","title":"Start"},
                    {"id":"hours","type":"hours","title":"Business Hours"},
                    {"id":"on_call","type":"condition","title":"On Call Check"},
                    {"id":"on_call_nurse","type":"queue","title":"On Call Nurse"},
                    {"id":"main_menu","type":"dtmf_menu","title":"Main Menu","options":[
                      {"label":"Appointments","next":"#appointments"},
                      {"label":"Pharmacy","next":"#pharmacy"},
                      {"label":"Billing","next":"#billing"},
                      {"label":"Triage","next":"#triage"}
                    ]},
                    {"id":"appointments","type":"queue","title":"Appointments Queue"},
                    {"id":"pharmacy","type":"transfer","title":"Pharmacy Transfer"},
                    {"id":"billing","type":"queue","title":"Billing Queue"},
                    {"id":"triage","type":"queue","title":"Triage Queue"},
                    {"id":"nurse_line","type":"transfer","title":"Nurse Line"},
                    {"id":"queue","type":"queue","title":"General Queue"},
                    {"id":"appointments_queue","type":"queue","title":"Appointments Queue"},
                    {"id":"transfer_to_receptionist","type":"transfer","title":"Transfer to Receptionist"},
                    {"id":"end","type":"end","title":"End Call"}
                  ],
                  "edges":[
                    {"id":"e1","source":"start","sourcePort":"out","target":"hours","targetPort":"in"},
                    {"id":"e2","source":"hours","sourcePort":"out","target":"on_call","targetPort":"in"},
                    {"id":"e3","source":"on_call","sourcePort":"true","target":"main_menu","targetPort":"in"},
                    {"id":"e4","source":"main_menu","sourcePort":"key1","target":"appointments","targetPort":"in"},
                    {"id":"e5","source":"main_menu","sourcePort":"key2","target":"pharmacy","targetPort":"in"},
                    {"id":"e6","source":"main_menu","sourcePort":"key3","target":"billing","targetPort":"in"},
                    {"id":"e7","source":"main_menu","sourcePort":"key4","target":"triage","targetPort":"in"}
                  ]
                }}
                """;

        FlowModel model = UnifiedAiEngine.tryParseJsonNodesFromNested(healthcareJson);
        assertNotNull(model);
        assertEquals("Hospital IVR", model.getName());
        assertEquals(14, model.getNodes().size());
        assertTrue(model.getNodes().stream().anyMatch(n -> "start".equals(n.getId())));
        assertTrue(model.getNodes().stream().anyMatch(n -> "hours".equals(n.getId())));
        assertTrue(model.getNodes().stream().anyMatch(n -> "on_call".equals(n.getId())));
        assertTrue(model.getNodes().stream().anyMatch(n -> "main_menu".equals(n.getId())));
        assertTrue(model.getNodes().stream().anyMatch(n -> "appointments".equals(n.getId())));
        assertTrue(model.getNodes().stream().anyMatch(n -> "pharmacy".equals(n.getId())));
        assertTrue(model.getNodes().stream().anyMatch(n -> "billing".equals(n.getId())));
        assertTrue(model.getNodes().stream().anyMatch(n -> "triage".equals(n.getId())));
        assertTrue(model.getNodes().stream().anyMatch(n -> "nurse_line".equals(n.getId())));
        assertTrue(model.getNodes().stream().anyMatch(n -> "queue".equals(n.getId())));
        assertTrue(model.getNodes().stream().anyMatch(n -> "appointments_queue".equals(n.getId())));
        assertTrue(model.getNodes().stream().anyMatch(n -> "transfer_to_receptionist".equals(n.getId())));
        assertTrue(model.getNodes().stream().anyMatch(n -> "end".equals(n.getId())));
        assertTrue(model.getConnections().size() >= 7);
    }

    @Test
    @DisplayName("Should parse nested banking forms JSON with embedded connections")
    void testConvertNestedBankingFormsJson() {
        String bankingJson = """
                {"name":"Banking IVR","forms":[
                  {"id":"start","type":"start","blocks":[
                    {"prompts":["Welcome to Global Bank"],"goto":{"next":"authenticate"}}
                  ]},
                  {"id":"authenticate","type":"dtmf_input","blocks":[
                    {"prompts":["Please enter your card number"],"goto":{"next":"pin"}}
                  ]},
                  {"id":"pin","type":"dtmf_input","blocks":[
                    {"prompts":["Please enter your PIN"],"goto":{"next":"menu"}}
                  ]},
                  {"id":"menu","type":"dtmf_menu","menu":{
                    "prompts":["Press 1 for balance, 2 for cards, 3 for loans, 4 for agent, 0 to end"],
                    "choices":[
                      {"accept":"1","next":"balance"},
                      {"accept":"2","next":"cards"},
                      {"accept":"3","next":"loans"},
                      {"accept":"4","next":"agent"},
                      {"accept":"0","next":"end"}
                    ]
                  }},
                  {"id":"balance","type":"queue","blocks":[
                    {"prompts":["Your balance is 500 dollars"],"goto":{"next":"menu"}}
                  ]},
                  {"id":"cards","type":"transfer","blocks":[
                    {"prompts":["Transferring to cards"],"transfers":[{"dest":"+3002"}]}
                  ]},
                  {"id":"loans","type":"transfer","blocks":[
                    {"prompts":["Transferring to loans"],"transfers":[{"dest":"+3003"}]}
                  ]},
                  {"id":"agent","type":"transfer","blocks":[
                    {"prompts":["Transferring to agent"],"transfers":[{"dest":"+3004"}]}
                  ]},
                  {"id":"end","type":"end","blocks":[
                    {"prompts":["Thank you for calling"],"disconnects":true}
                  ]}
                ]}
                """;

        FlowModel model = UnifiedAiEngine.tryParseJsonNodesFromNested(bankingJson);
        assertNotNull(model);
        assertEquals("Banking IVR", model.getName());
        assertEquals(9, model.getNodes().size());
        assertTrue(model.getNodes().stream().anyMatch(n -> "start".equals(n.getId())));
        assertTrue(model.getNodes().stream().anyMatch(n -> "authenticate".equals(n.getId())));
        assertTrue(model.getNodes().stream().anyMatch(n -> "pin".equals(n.getId())));
        assertTrue(model.getNodes().stream().anyMatch(n -> "menu".equals(n.getId())));
        assertTrue(model.getNodes().stream().anyMatch(n -> "balance".equals(n.getId())));
        assertTrue(model.getNodes().stream().anyMatch(n -> "cards".equals(n.getId())));
        assertTrue(model.getNodes().stream().anyMatch(n -> "loans".equals(n.getId())));
        assertTrue(model.getNodes().stream().anyMatch(n -> "agent".equals(n.getId())));
        assertTrue(model.getNodes().stream().anyMatch(n -> "end".equals(n.getId())));
        assertTrue(model.getConnections().size() >= 7);
        assertTrue(model.getConnections().stream().anyMatch(c -> "start".equals(c.getSourceNodeId()) && "authenticate".equals(c.getTargetNodeId())));
        assertTrue(model.getConnections().stream().anyMatch(c -> "pin".equals(c.getSourceNodeId()) && "menu".equals(c.getTargetNodeId())));
        assertTrue(model.getConnections().stream().anyMatch(c -> "menu".equals(c.getSourceNodeId()) && "balance".equals(c.getTargetNodeId())));
        assertTrue(model.getConnections().stream().anyMatch(c -> "menu".equals(c.getSourceNodeId()) && "agent".equals(c.getTargetNodeId())));
        assertTrue(model.getConnections().stream().anyMatch(c -> "menu".equals(c.getSourceNodeId()) && "end".equals(c.getTargetNodeId())));
        assertTrue(model.getConnections().stream().anyMatch(c -> "balance".equals(c.getSourceNodeId()) && "menu".equals(c.getTargetNodeId())));
    }

    @Test
    @DisplayName("Should parse nested university forms JSON with embedded connections")
    void testConvertNestedUniversityFormsJson() {
        String universityJson = """
                {"name":"University IVR","forms":[
                  {"id":"start","type":"start","blocks":[
                    {"prompts":["Welcome to the university"],"goto":{"next":"greeting"}}
                  ]},
                  {"id":"greeting","type":"greeting","blocks":[
                    {"prompts":["Thank you for calling"],"goto":{"next":"mainMenu"}}
                  ]},
                  {"id":"mainMenu","type":"dtmf_menu","menu":{
                    "prompts":["Press 1 for admissions, 2 for financial aid, 3 for student services, 4 for registrar, 5 for emergency"],
                    "choices":[
                      {"accept":"1","next":"admissions"},
                      {"accept":"2","next":"financialAid"},
                      {"accept":"3","next":"studentServices"},
                      {"accept":"4","next":"registrar"},
                      {"accept":"5","next":"emergency"}
                    ]
                  }},
                  {"id":"admissions","type":"queue","blocks":[
                    {"prompts":["Connecting to admissions"],"goto":{"next":"mainMenu"}}
                  ]},
                  {"id":"financialAid","type":"queue","blocks":[
                    {"prompts":["Connecting to financial aid"],"goto":{"next":"mainMenu"}}
                  ]},
                  {"id":"studentServices","type":"queue","blocks":[
                    {"prompts":["Connecting to student services"],"goto":{"next":"mainMenu"}}
                  ]},
                  {"id":"registrar","type":"queue","blocks":[
                    {"prompts":["Connecting to registrar"],"goto":{"next":"mainMenu"}}
                  ]},
                  {"id":"emergency","type":"queue","blocks":[
                    {"prompts":["Connecting to emergency"],"goto":{"next":"mainMenu"}}
                  ]},
                  {"id":"mainDesk","type":"transfer","blocks":[
                    {"prompts":["Transferring to main desk"],"transfers":[{"dest":"+1000"}]}
                  ]},
                  {"id":"end","type":"end","blocks":[
                    {"prompts":["Goodbye"],"disconnects":true}
                  ]}
                ]}
                """;

        FlowModel model = UnifiedAiEngine.tryParseJsonNodesFromNested(universityJson);
        assertNotNull(model);
        assertEquals("University IVR", model.getName());
        assertEquals(10, model.getNodes().size());
        assertTrue(model.getNodes().stream().anyMatch(n -> "start".equals(n.getId())));
        assertTrue(model.getNodes().stream().anyMatch(n -> "mainMenu".equals(n.getId())));
        assertTrue(model.getNodes().stream().anyMatch(n -> "admissions".equals(n.getId())));
        assertTrue(model.getNodes().stream().anyMatch(n -> "financialAid".equals(n.getId())));
        assertTrue(model.getNodes().stream().anyMatch(n -> "studentServices".equals(n.getId())));
        assertTrue(model.getNodes().stream().anyMatch(n -> "registrar".equals(n.getId())));
        assertTrue(model.getNodes().stream().anyMatch(n -> "emergency".equals(n.getId())));
        assertTrue(model.getNodes().stream().anyMatch(n -> "mainDesk".equals(n.getId())));
        assertTrue(model.getNodes().stream().anyMatch(n -> "end".equals(n.getId())));
        assertTrue(model.getConnections().size() >= 7);
        assertTrue(model.getConnections().stream().anyMatch(c -> "start".equals(c.getSourceNodeId()) && "greeting".equals(c.getTargetNodeId())));
        assertTrue(model.getConnections().stream().anyMatch(c -> "greeting".equals(c.getSourceNodeId()) && "mainMenu".equals(c.getTargetNodeId())));
        assertTrue(model.getConnections().stream().anyMatch(c -> "mainMenu".equals(c.getSourceNodeId()) && "admissions".equals(c.getTargetNodeId())));
        assertTrue(model.getConnections().stream().anyMatch(c -> "mainMenu".equals(c.getSourceNodeId()) && "emergency".equals(c.getTargetNodeId())));
        assertTrue(model.getConnections().stream().anyMatch(c -> "admissions".equals(c.getSourceNodeId()) && "mainMenu".equals(c.getTargetNodeId())));
    }

    private FlowModel buildMockFlowModel(String flowId, int numNodes) {
        FlowModel model = new FlowModel();
        model.setId(flowId);
        model.setName("Test Flow " + flowId);
        model.setDescription("Mock flow for testing");
        for (int i = 1; i <= numNodes; i++) {
            FlowNode node = new FlowNode("n" + i, FlowNodeType.PROMPT, "Node " + i);
            node.setSubtitle("Subtitle " + i);
            model.addNode(node);
        }
        return model;
    }
}
