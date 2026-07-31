package com.nexusivr.ai.dto.patch;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Response containing a list of patch operations to apply to a flow.
 */
public class FlowPatchResponse {

    private List<FlowPatchOperation> patches;
    private String rationale;
    private boolean applied;

    public FlowPatchResponse() {
        this.patches = new ArrayList<>();
        this.applied = false;
    }

    public FlowPatchResponse(List<FlowPatchOperation> patches, String rationale) {
        this.patches = patches != null ? patches : new ArrayList<>();
        this.rationale = rationale;
        this.applied = false;
    }

    public List<FlowPatchOperation> getPatches() {
        return patches;
    }

    public void setPatches(List<FlowPatchOperation> patches) {
        this.patches = patches;
    }

    public String getRationale() {
        return rationale;
    }

    public void setRationale(String rationale) {
        this.rationale = rationale;
    }

    public boolean isApplied() {
        return applied;
    }

    public void setApplied(boolean applied) {
        this.applied = applied;
    }

    @Override
    public String toString() {
        return "FlowPatchResponse{" +
                "patches=" + patches.size() +
                ", rationale='" + rationale + '\'' +
                ", applied=" + applied +
                '}';
    }
}
