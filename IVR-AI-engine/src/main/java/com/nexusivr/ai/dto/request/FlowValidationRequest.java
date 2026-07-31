package com.nexusivr.ai.dto.request;

import com.nexusivr.ai.dto.common.FlowDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Input for the Validation module. rulesetsToApply is optional — an
 * empty/null list means "run every default ruleset" (reachability,
 * dangling edges, missing terminal nodes, etc.); naming specific
 * rulesets (e.g. "REACHABILITY", "DTMF_COMPLETENESS") lets a caller run
 * a cheap partial check, such as inside an editor's autosave hook,
 * without paying for the full validation suite on every keystroke.
 */
public class FlowValidationRequest {

    private UUID tenantId;
    private FlowDto flow;
    private List<String> rulesetsToApply;

    public FlowValidationRequest() {
        this.rulesetsToApply = new ArrayList<>();
    }

    public FlowValidationRequest(UUID tenantId, FlowDto flow, List<String> rulesetsToApply) {
        this.tenantId = tenantId;
        this.flow = flow;
        this.rulesetsToApply = rulesetsToApply != null ? rulesetsToApply : new ArrayList<>();
    }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public FlowDto getFlow() { return flow; }
    public void setFlow(FlowDto flow) { this.flow = flow; }

    public List<String> getRulesetsToApply() { return rulesetsToApply; }
    public void setRulesetsToApply(List<String> rulesetsToApply) { this.rulesetsToApply = rulesetsToApply; }

    @Override
    public String toString() {
        return "FlowValidationRequest{" +
                "tenantId=" + tenantId +
                ", flow=" + flow +
                ", rulesetsToApply=" + rulesetsToApply +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FlowValidationRequest)) return false;
        FlowValidationRequest that = (FlowValidationRequest) o;
        return Objects.equals(tenantId, that.tenantId) && Objects.equals(flow, that.flow) &&
                Objects.equals(rulesetsToApply, that.rulesetsToApply);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, flow, rulesetsToApply);
    }
}
