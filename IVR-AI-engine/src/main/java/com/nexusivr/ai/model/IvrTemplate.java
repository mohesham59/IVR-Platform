package com.nexusivr.ai.model;

import java.util.List;

/**
 * Reusable IVR template details for specific business domains.
 */
public class IvrTemplate {
    private final String domainName;
    private final String description;
    private final List<String> recommendedNodeOrder;
    private final String standardRouting;
    private final String commonMenuLayout;
    private final List<String> recommendedQueues;
    private final List<String> typicalTransfers;
    private final String commonGreetings;
    private final String businessHoursPlacement;
    private final String errorHandling;
    private final String timeoutHandling;
    private final String templateFlowJson;

    public IvrTemplate(String domainName, String description, List<String> recommendedNodeOrder,
                       String standardRouting, String commonMenuLayout, List<String> recommendedQueues,
                       List<String> typicalTransfers, String commonGreetings, String businessHoursPlacement,
                       String errorHandling, String timeoutHandling, String templateFlowJson) {
        this.domainName = domainName;
        this.description = description;
        this.recommendedNodeOrder = recommendedNodeOrder;
        this.standardRouting = standardRouting;
        this.commonMenuLayout = commonMenuLayout;
        this.recommendedQueues = recommendedQueues;
        this.typicalTransfers = typicalTransfers;
        this.commonGreetings = commonGreetings;
        this.businessHoursPlacement = businessHoursPlacement;
        this.errorHandling = errorHandling;
        this.timeoutHandling = timeoutHandling;
        this.templateFlowJson = templateFlowJson;
    }

    public String getDomainName() { return domainName; }
    public String getDescription() { return description; }
    public List<String> getRecommendedNodeOrder() { return recommendedNodeOrder; }
    public String getStandardRouting() { return standardRouting; }
    public String getCommonMenuLayout() { return commonMenuLayout; }
    public List<String> getRecommendedQueues() { return recommendedQueues; }
    public List<String> getTypicalTransfers() { return typicalTransfers; }
    public String getCommonGreetings() { return commonGreetings; }
    public String getBusinessHoursPlacement() { return businessHoursPlacement; }
    public String getErrorHandling() { return errorHandling; }
    public String getTimeoutHandling() { return timeoutHandling; }
    public String getTemplateFlowJson() { return templateFlowJson; }
}
