package com.nexusivr.ai.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable flow snapshot representing a historical version of an IVR flow.
 */
public class FlowSnapshot {
    private final UUID snapshotId;
    private final UUID flowId;
    private final int version;
    private final String flowJson;
    private final Instant createdAt;

    public FlowSnapshot(UUID snapshotId, UUID flowId, int version, String flowJson, Instant createdAt) {
        this.snapshotId = snapshotId;
        this.flowId = flowId;
        this.version = version;
        this.flowJson = flowJson;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public UUID getSnapshotId() { return snapshotId; }
    public UUID getFlowId() { return flowId; }
    public int getVersion() { return version; }
    public String getFlowJson() { return flowJson; }
    public Instant getCreatedAt() { return createdAt; }
}
