package com.nexusivr.ai.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class NodeLibraryTest {

    @Test
    public void testNodeLibraryContainsTwentySupportedNodes() {
        assertEquals(20, NodeLibrary.SUPPORTED_TYPES.size());
        assertTrue(NodeLibrary.SUPPORTED_TYPES.containsKey("start"));
        assertTrue(NodeLibrary.SUPPORTED_TYPES.containsKey("greeting"));
        assertTrue(NodeLibrary.SUPPORTED_TYPES.containsKey("playback"));
        assertTrue(NodeLibrary.SUPPORTED_TYPES.containsKey("tts"));
        assertTrue(NodeLibrary.SUPPORTED_TYPES.containsKey("dtmf_menu"));
        assertTrue(NodeLibrary.SUPPORTED_TYPES.containsKey("dtmf_input"));
        assertTrue(NodeLibrary.SUPPORTED_TYPES.containsKey("queue"));
        assertTrue(NodeLibrary.SUPPORTED_TYPES.containsKey("transfer"));
        assertTrue(NodeLibrary.SUPPORTED_TYPES.containsKey("extension"));
        assertTrue(NodeLibrary.SUPPORTED_TYPES.containsKey("voicemail"));
        assertTrue(NodeLibrary.SUPPORTED_TYPES.containsKey("record"));
        assertTrue(NodeLibrary.SUPPORTED_TYPES.containsKey("api"));
        assertTrue(NodeLibrary.SUPPORTED_TYPES.containsKey("database"));
        assertTrue(NodeLibrary.SUPPORTED_TYPES.containsKey("hours"));
        assertTrue(NodeLibrary.SUPPORTED_TYPES.containsKey("holiday"));
        assertTrue(NodeLibrary.SUPPORTED_TYPES.containsKey("condition"));
        assertTrue(NodeLibrary.SUPPORTED_TYPES.containsKey("variable"));
        assertTrue(NodeLibrary.SUPPORTED_TYPES.containsKey("webhook"));
        assertTrue(NodeLibrary.SUPPORTED_TYPES.containsKey("ai"));
        assertTrue(NodeLibrary.SUPPORTED_TYPES.containsKey("end"));
    }

    @Test
    public void testLibraryStringFormatting() {
        String formatted = NodeLibrary.getLibraryString();
        assertNotNull(formatted);
        assertTrue(formatted.contains("- start:"));
        assertTrue(formatted.contains("- end:"));
    }
}
