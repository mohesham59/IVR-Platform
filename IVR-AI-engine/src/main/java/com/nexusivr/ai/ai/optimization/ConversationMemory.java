package com.nexusivr.ai.ai.optimization;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Conversation memory with sliding window and summarization support.
 * <p>
 * Keeps a bounded history of messages per session. Older messages
 * beyond the window are summarized to save tokens.
 * </p>
 */
public class ConversationMemory {

    private static final ConversationMemory INSTANCE = new ConversationMemory();

    public static ConversationMemory getInstance() {
        return INSTANCE;
    }

    private final Map<UUID, Deque<MemoryEntry>> memories = new ConcurrentHashMap<>();
    private final int maxWindowSize;
    private final int summarizeAfterTurns;

    public ConversationMemory() {
        this(20, 10);
    }

    public ConversationMemory(int maxWindowSize, int summarizeAfterTurns) {
        this.maxWindowSize = maxWindowSize;
        this.summarizeAfterTurns = summarizeAfterTurns;
    }

    public void addEntry(UUID sessionId, String role, String content) {
        Deque<MemoryEntry> deque = memories.computeIfAbsent(sessionId, k -> new ArrayDeque<>());
        synchronized (deque) {
            deque.addLast(new MemoryEntry(role, content, System.currentTimeMillis()));
            while (deque.size() > maxWindowSize) {
                deque.removeFirst();
            }
        }
    }

    public List<MemoryEntry> getRecentEntries(UUID sessionId, int count) {
        Deque<MemoryEntry> deque = memories.get(sessionId);
        if (deque == null) return List.of();

        synchronized (deque) {
            List<MemoryEntry> list = new ArrayList<>(deque);
            int start = Math.max(0, list.size() - count);
            return list.subList(start, list.size());
        }
    }

    public List<MemoryEntry> getEntriesForLlm(UUID sessionId) {
        Deque<MemoryEntry> deque = memories.get(sessionId);
        if (deque == null) return List.of();

        synchronized (deque) {
            List<MemoryEntry> list = new ArrayList<>(deque);
            if (list.size() > summarizeAfterTurns) {
                int excess = list.size() - summarizeAfterTurns;
                List<MemoryEntry> toSummarize = list.subList(0, excess);
                String summary = summarizeEntries(toSummarize);
                list = new ArrayList<>();
                list.add(new MemoryEntry("system", summary, System.currentTimeMillis()));
                list.addAll(list.subList(excess, list.size()));
            }
            return list;
        }
    }

    public void clear(UUID sessionId) {
        Deque<MemoryEntry> deque = memories.remove(sessionId);
        if (deque != null) {
            synchronized (deque) {
                deque.clear();
            }
        }
    }

    public int getEntryCount(UUID sessionId) {
        Deque<MemoryEntry> deque = memories.get(sessionId);
        return deque != null ? deque.size() : 0;
    }

    private String summarizeEntries(List<MemoryEntry> entries) {
        StringBuilder sb = new StringBuilder();
        sb.append("Previous conversation summary (").append(entries.size()).append(" turns):\n");
        for (MemoryEntry entry : entries) {
            sb.append("- ").append(entry.role).append(": ").append(truncate(entry.content, 100)).append("\n");
        }
        return sb.toString();
    }

    private static String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }

    public record MemoryEntry(String role, String content, long timestamp) {
    }
}
