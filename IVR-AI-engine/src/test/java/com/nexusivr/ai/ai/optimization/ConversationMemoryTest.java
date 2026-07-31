package com.nexusivr.ai.ai.optimization;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ConversationMemoryTest {

    @Test
    void testAddEntry_storesEntry() {
        ConversationMemory memory = new ConversationMemory(10, 5);
        UUID sessionId = UUID.randomUUID();
        memory.addEntry(sessionId, "user", "Hello");
        assertEquals(1, memory.getEntryCount(sessionId));
    }

    @Test
    void testGetRecentEntries_returnsMostRecent() {
        ConversationMemory memory = new ConversationMemory(10, 5);
        UUID sessionId = UUID.randomUUID();
        memory.addEntry(sessionId, "user", "First");
        memory.addEntry(sessionId, "assistant", "Second");
        memory.addEntry(sessionId, "user", "Third");

        List<ConversationMemory.MemoryEntry> recent = memory.getRecentEntries(sessionId, 2);
        assertEquals(2, recent.size());
        assertEquals("Second", recent.get(0).content());
        assertEquals("Third", recent.get(1).content());
    }

    @Test
    void testGetEntriesForLlm_returnsEntriesForLlm() {
        ConversationMemory memory = new ConversationMemory(10, 5);
        UUID sessionId = UUID.randomUUID();
        memory.addEntry(sessionId, "user", "Hello");
        memory.addEntry(sessionId, "assistant", "Hi there");

        List<ConversationMemory.MemoryEntry> entries = memory.getEntriesForLlm(sessionId);
        assertNotNull(entries);
        assertFalse(entries.isEmpty());
    }

    @Test
    void testClear_removesAllEntries() {
        ConversationMemory memory = new ConversationMemory(10, 5);
        UUID sessionId = UUID.randomUUID();
        memory.addEntry(sessionId, "user", "Hello");
        assertEquals(1, memory.getEntryCount(sessionId));
        memory.clear(sessionId);
        assertEquals(0, memory.getEntryCount(sessionId));
    }

    @Test
    void testGetEntryCount_emptySession_returnsZero() {
        ConversationMemory memory = new ConversationMemory(10, 5);
        assertEquals(0, memory.getEntryCount(UUID.randomUUID()));
    }

    @Test
    void testSlidingWindow_evictsOldEntries() {
        ConversationMemory smallMemory = new ConversationMemory(3, 2);
        UUID sid = UUID.randomUUID();
        for (int i = 0; i < 10; i++) {
            smallMemory.addEntry(sid, "user", "Message " + i);
        }
        assertEquals(3, smallMemory.getEntryCount(sid));
    }

    @Test
    void testSingletonInstance() {
        ConversationMemory instance1 = ConversationMemory.getInstance();
        ConversationMemory instance2 = ConversationMemory.getInstance();
        assertSame(instance1, instance2);
    }
}