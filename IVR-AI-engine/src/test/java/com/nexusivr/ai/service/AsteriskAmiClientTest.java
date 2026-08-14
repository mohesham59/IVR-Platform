package com.nexusivr.ai.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class AsteriskAmiClientTest {

    private AsteriskAmiClient amiClient;

    @BeforeEach
    public void setUp() {
        amiClient = new AsteriskAmiClient();
    }

    @Test
    public void testParseQueueParamsEvent() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Queue", "Support L1");
        headers.put("Calls", "4");
        headers.put("Holdtime", "45");
        headers.put("LoggedIn", "6");
        headers.put("Available", "4");

        amiClient.parseAmiEventHeader("QueueParams", headers);

        AsteriskAmiClient.LiveQueueStats stats = amiClient.getLiveStats("Support L1");
        assertNotNull(stats);
        assertEquals("Support L1", stats.queueName);
        assertEquals(4, stats.waitingCalls);
        assertEquals(45, stats.avgWaitSeconds);
        assertEquals(6, stats.memberCount);
        assertEquals(4, stats.activeMembers);
    }

    @Test
    public void testParseQueueMemberPauseEvent() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Location", "PJSIP/1001");
        headers.put("Paused", "1");

        amiClient.parseAmiEventHeader("QueueMemberPause", headers);

        String state = amiClient.getAgentState("PJSIP/1001");
        assertEquals("paused", state);

        headers.put("Paused", "0");
        amiClient.parseAmiEventHeader("QueueMemberPause", headers);
        state = amiClient.getAgentState("PJSIP/1001");
        assertEquals("available", state);
    }

    @Test
    public void testParseQueueMemberStatusEvent() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Location", "PJSIP/1002");
        headers.put("Status", "2"); // InUse

        amiClient.parseAmiEventHeader("QueueMemberStatus", headers);

        String state = amiClient.getAgentState("PJSIP/1002");
        assertEquals("in_call", state);
    }

    @Test
    public void testGetAmiHealthStatusStructure() {
        Map<String, Object> health = amiClient.getAmiHealthStatus();
        assertNotNull(health);
        assertTrue(health.containsKey("host"));
        assertTrue(health.containsKey("port"));
        assertTrue(health.containsKey("connected"));
        assertTrue(health.containsKey("status"));
    }
}
