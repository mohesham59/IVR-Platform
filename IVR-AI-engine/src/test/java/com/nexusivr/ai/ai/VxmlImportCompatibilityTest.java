package com.nexusivr.ai.ai;

import com.nexusivr.ai.config.LlmConfig;
import com.nexusivr.ai.model.flow.*;
import com.nexusivr.ai.service.VxmlToModelConverter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VxmlImportCompatibilityTest {

    @BeforeAll
    static void initConfig() {
        // Initialize config loading
        LlmConfig.getScenariosDir();
    }

    @Test
    void testCinemaBookingImport() throws Exception {
        System.out.println("DEBUG SCENARIOS DIR: " + LlmConfig.getScenariosDir());
        Path path = Path.of(LlmConfig.getScenariosDir(), "cinema-booking-001.vxml");
        System.out.println("DEBUG CINEMA PATH: " + path.toAbsolutePath());
        assertTrue(Files.exists(path), "cinema-booking-001.vxml must exist");

        String vxml = Files.readString(path, StandardCharsets.UTF_8);
        VxmlToModelConverter converter = new VxmlToModelConverter();
        FlowModel model = converter.convert(vxml);

        assertNotNull(model);
        
        // Assert correct node count (6 dialog elements: welcome, main_menu, book_tickets, movies_info, location_info, transfer_operator)
        List<FlowNode> nodes = model.getNodes();
        assertEquals(6, nodes.size(), "Should parse exactly 6 nodes from cinema scenario");

        // Assert correct Start node identification
        FlowNode welcomeNode = nodes.stream().filter(n -> "welcome".equals(n.getId())).findFirst().orElse(null);
        assertNotNull(welcomeNode);
        assertEquals(FlowNodeType.START, welcomeNode.getType(), "welcome node must default to START as the first element");

        // Assert correct Menu node parsing
        FlowNode mainMenuNode = nodes.stream().filter(n -> "main_menu".equals(n.getId())).findFirst().orElse(null);
        assertNotNull(mainMenuNode);
        assertEquals(FlowNodeType.MENU, mainMenuNode.getType(), "main_menu must be parsed as MENU type");
        assertNotNull(mainMenuNode.getMenu());
        assertEquals(4, mainMenuNode.getMenu().getChoices().size(), "main_menu must have exactly 4 choices parsed");

        // Assert correct Input node parsing
        FlowNode bookTicketsNode = nodes.stream().filter(n -> "book_tickets".equals(n.getId())).findFirst().orElse(null);
        assertNotNull(bookTicketsNode);
        assertEquals(FlowNodeType.INPUT, bookTicketsNode.getType(), "book_tickets must be parsed as INPUT due to field child tags");

        // Assert correct Transfer node (mapped dynamically or identified as fallback)
        FlowNode transferNode = nodes.stream().filter(n -> "transfer_operator".equals(n.getId())).findFirst().orElse(null);
        assertNotNull(transferNode);

        // Assert non-zero, correct connection count
        List<FlowConnection> connections = model.getConnections();
        assertTrue(connections.size() > 0, "Connections count must be non-zero");
        
        // Assert choice connections from main_menu
        long choiceConns = connections.stream().filter(c -> "main_menu".equals(c.getSourceNodeId())).count();
        assertEquals(4, choiceConns, "main_menu must have 4 outgoing choice connections");

        // Assert goto connection from welcome to main_menu
        boolean welcomeToMenuConn = connections.stream().anyMatch(c -> 
                "welcome".equals(c.getSourceNodeId()) && "main_menu".equals(c.getTargetNodeId()));
        assertTrue(welcomeToMenuConn, "Must have a connection from welcome to main_menu");
    }

    @Test
    void testRestaurantBookingImport() throws Exception {
        Path path = Path.of(LlmConfig.getScenariosDir(), "restaurant-booking-001.vxml");
        assertTrue(Files.exists(path), "restaurant-booking-001.vxml must exist");

        String vxml = Files.readString(path, StandardCharsets.UTF_8);
        VxmlToModelConverter converter = new VxmlToModelConverter();
        FlowModel model = converter.convert(vxml);

        assertNotNull(model);
        List<FlowNode> nodes = model.getNodes();
        assertEquals(6, nodes.size(), "Should parse exactly 6 nodes from restaurant scenario");

        FlowNode welcomeNode = nodes.stream().filter(n -> "welcome".equals(n.getId())).findFirst().orElse(null);
        assertNotNull(welcomeNode);
        assertEquals(FlowNodeType.START, welcomeNode.getType());

        FlowNode mainMenuNode = nodes.stream().filter(n -> "main_menu".equals(n.getId())).findFirst().orElse(null);
        assertNotNull(mainMenuNode);
        assertEquals(FlowNodeType.MENU, mainMenuNode.getType());
        assertNotNull(mainMenuNode.getMenu());
        assertEquals(4, mainMenuNode.getMenu().getChoices().size());

        List<FlowConnection> connections = model.getConnections();
        assertTrue(connections.size() > 0);

        boolean welcomeToMenuConn = connections.stream().anyMatch(c -> 
                "welcome".equals(c.getSourceNodeId()) && "main_menu".equals(c.getTargetNodeId()));
        assertTrue(welcomeToMenuConn);

        long choiceConns = connections.stream().filter(c -> "main_menu".equals(c.getSourceNodeId())).count();
        assertEquals(4, choiceConns);
    }

    @Test
    void testGovernmentIvrDraftImport() throws Exception {
        Path path = Path.of(LlmConfig.getScenariosDir(), "government_ivr_draft.vxml");
        assertTrue(Files.exists(path), "government_ivr_draft.vxml must exist");

        String vxml = Files.readString(path, StandardCharsets.UTF_8);
        VxmlToModelConverter converter = new VxmlToModelConverter();
        FlowModel model = converter.convert(vxml);

        assertNotNull(model);
        List<FlowNode> nodes = model.getNodes();
        assertEquals(21, nodes.size(), "Should parse exactly 21 nodes from government scenario");

        // Verify distinct titles extracted from VXML comments
        FlowNode n4 = nodes.stream().filter(n -> "n4".equals(n.getId())).findFirst().orElse(null);
        assertNotNull(n4);
        assertEquals("Passport new", n4.getTitle());

        FlowNode n5 = nodes.stream().filter(n -> "n5".equals(n.getId())).findFirst().orElse(null);
        assertNotNull(n5);
        assertEquals("Passport renew", n5.getTitle());

        FlowNode n6 = nodes.stream().filter(n -> "n6".equals(n.getId())).findFirst().orElse(null);
        assertNotNull(n6);
        assertEquals("Passport replace", n6.getTitle());

        FlowNode n7 = nodes.stream().filter(n -> "n7".equals(n.getId())).findFirst().orElse(null);
        assertNotNull(n7);
        assertEquals("Passport child", n7.getTitle());
    }
}
