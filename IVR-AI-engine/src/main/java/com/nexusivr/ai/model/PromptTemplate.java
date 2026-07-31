package com.nexusivr.ai.model;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Plain Java model for a row in {@code prompt_templates}.
 *
 * A versioned, tenant-customizable (or global, when {@code tenantId}
 * is {@code null}) prompt asset. Deliberately holds no foreign key
 * into the session graph — prompt authoring is an independent
 * lifecycle from conversation runtime, so this class stands alone,
 * with no relationship fields to any other model in this package.
 */
public class PromptTemplate {

    private UUID id;
    private UUID tenantId;
    private PromptModule module;
    private String templateKey;
    private int version;
    private String content;
    private Map<String, Object> variables;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;

    public PromptTemplate() {
        this.variables = new HashMap<>();
    }

    public PromptTemplate(UUID id,
                           UUID tenantId,
                           PromptModule module,
                           String templateKey,
                           int version,
                           String content,
                           Map<String, Object> variables,
                           boolean active,
                           Instant createdAt,
                           Instant updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.module = module;
        this.templateKey = templateKey;
        this.version = version;
        this.content = content;
        this.variables = variables != null ? variables : new HashMap<>();
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public PromptModule getModule() {
        return module;
    }

    public void setModule(PromptModule module) {
        this.module = module;
    }

    public String getTemplateKey() {
        return templateKey;
    }

    public void setTemplateKey(String templateKey) {
        this.templateKey = templateKey;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public void setVariables(Map<String, Object> variables) {
        this.variables = variables;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PromptTemplate)) {
            return false;
        }
        PromptTemplate that = (PromptTemplate) o;
        return version == that.version
                && active == that.active
                && Objects.equals(id, that.id)
                && Objects.equals(tenantId, that.tenantId)
                && module == that.module
                && Objects.equals(templateKey, that.templateKey)
                && Objects.equals(content, that.content)
                && Objects.equals(variables, that.variables)
                && Objects.equals(createdAt, that.createdAt)
                && Objects.equals(updatedAt, that.updatedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, tenantId, module, templateKey, version,
                content, variables, active, createdAt, updatedAt);
    }

    @Override
    public String toString() {
        return "PromptTemplate{" +
                "id=" + id +
                ", tenantId=" + tenantId +
                ", module=" + module +
                ", templateKey='" + templateKey + '\'' +
                ", version=" + version +
                ", content='" + content + '\'' +
                ", variables=" + variables +
                ", active=" + active +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
