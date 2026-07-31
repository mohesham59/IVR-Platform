package com.nexusivr.ai.ai.optimization;

import com.nexusivr.ai.model.flow.FlowModel;
import com.nexusivr.ai.model.flow.FlowNode;
import com.nexusivr.ai.model.flow.FlowConnection;
import com.nexusivr.ai.model.flow.FlowNodeType;
import com.nexusivr.ai.model.flow.FlowPrompt;
import com.nexusivr.ai.model.flow.FlowMenu;
import com.nexusivr.ai.model.flow.FlowChoice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FlowSummaryBuilderTest {

    private FlowModel flowModel;

    @BeforeEach
    void setUp() {
        flowModel = new FlowModel();
        flowModel.setName("Test IVR");
        flowModel.setDescription("Restaurant reservation IVR");

        FlowNode node1 = new FlowNode();
        node1.setId("n1");
        node1.setType(FlowNodeType.MENU);
        node1.setPrompt(new FlowPrompt("Welcome to our restaurant."));
        FlowMenu menu1 = new FlowMenu();
        menu1.addChoice(new FlowChoice("1", "Reservations", "n2"));
        menu1.addChoice(new FlowChoice("2", "Hours", "n3"));
        node1.setMenu(menu1);

        FlowNode node2 = new FlowNode();
        node2.setId("n2");
        node2.setType(FlowNodeType.INPUT);
        node2.setPrompt(new FlowPrompt("Please enter your reservation time."));

        FlowNode node3 = new FlowNode();
        node3.setId("n3");
        node3.setType(FlowNodeType.INPUT);
        node3.setPrompt(new FlowPrompt("Please enter your party size."));

        flowModel.getNodes().addAll(List.of(node1, node2, node3));
        flowModel.getConnections().addAll(List.of(
                new FlowConnection("e1", "n1", "1", "n2", "in"),
                new FlowConnection("e2", "n1", "2", "n3", "in")
        ));
    }

    @Test
    void testBuildCompactSummary_returnsNonEmptyString() {
        String summary = FlowSummaryBuilder.buildCompactSummary(flowModel);
        assertNotNull(summary);
        assertFalse(summary.isBlank());
    }

    @Test
    void testBuildCompactSummary_containsFlowName() {
        String summary = FlowSummaryBuilder.buildCompactSummary(flowModel);
        assertTrue(summary.contains("Test IVR"));
    }

    @Test
    void testBuildCompactSummary_containsDescription() {
        String summary = FlowSummaryBuilder.buildCompactSummary(flowModel);
        assertTrue(summary.contains("restaurant"));
    }

    @Test
    void testBuildCompactSummary_containsNodeCount() {
        String summary = FlowSummaryBuilder.buildCompactSummary(flowModel);
        assertTrue(summary.contains("3") || summary.contains("nodes"));
    }

    @Test
    void testBuildCompactSummary_isCompact() {
        String summary = FlowSummaryBuilder.buildCompactSummary(flowModel);
        assertTrue(summary.length() < 2000, "Summary should be under 2000 chars");
    }

    @Test
    void testBuildCompactSummary_doesNotContainVoiceXml() {
        String summary = FlowSummaryBuilder.buildCompactSummary(flowModel);
        assertFalse(summary.contains("<vxml"), "Summary should not contain VoiceXML");
    }

@Test
    void testBuildCompactSummary_nullModel_returnsEmptyMarker() {
        String summary = FlowSummaryBuilder.buildCompactSummary(null);
        assertNotNull(summary);
        assertEquals("[Empty flow]", summary);
    }

    @Test
    void testBuildCompactSummary_emptyModel_returnsMinimal() {
        FlowModel empty = new FlowModel();
        String summary = FlowSummaryBuilder.buildCompactSummary(empty);
        assertNotNull(summary);
    }
}