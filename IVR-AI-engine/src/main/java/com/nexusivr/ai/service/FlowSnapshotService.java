package com.nexusivr.ai.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nexusivr.ai.controller.ServiceRegistry;
import com.nexusivr.ai.model.FlowSnapshot;
import com.nexusivr.ai.model.Message;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service responsible for managing immutable flow history and snapshots.
 */
public class FlowSnapshotService {

    private final Map<UUID, FlowSnapshot> snapshots = new ConcurrentHashMap<>();
    private final Map<UUID, List<FlowSnapshot>> conversationSnapshots = new ConcurrentHashMap<>();

    public FlowSnapshot createSnapshot(UUID conversationId, UUID flowId, String flowJson) {
        UUID snapshotId = UUID.randomUUID();
        int version = getNextVersion(conversationId);
        FlowSnapshot snapshot = new FlowSnapshot(snapshotId, flowId, version, flowJson, Instant.now());
        
        snapshots.put(snapshotId, snapshot);
        conversationSnapshots.computeIfAbsent(conversationId, id -> new ArrayList<>()).add(snapshot);
        return snapshot;
    }

    public FlowSnapshot getSnapshot(UUID snapshotId) {
        if (snapshotId == null) return null;
        return snapshots.get(snapshotId);
    }

    public List<FlowSnapshot> getSnapshotsForConversation(UUID conversationId) {
        return conversationSnapshots.getOrDefault(conversationId, List.of());
    }

    public FlowSnapshot getLatestSnapshot(UUID conversationId) {
        List<FlowSnapshot> list = getSnapshotsForConversation(conversationId);
        if (list.isEmpty()) return null;
        return list.get(list.size() - 1);
    }

    private int getNextVersion(UUID conversationId) {
        return getSnapshotsForConversation(conversationId).size() + 1;
    }

    public void clear() {
        snapshots.clear();
        conversationSnapshots.clear();
    }
}
