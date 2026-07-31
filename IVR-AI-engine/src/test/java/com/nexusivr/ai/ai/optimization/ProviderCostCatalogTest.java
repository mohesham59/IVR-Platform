package com.nexusivr.ai.ai.optimization;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProviderCostCatalogTest {

    @Test
    void testGetTierMultiplier_returnsValidMultiplier() {
        double multiplier = ProviderCostCatalog.getTierMultiplier("llama-3.3-70b-versatile");
        assertTrue(multiplier > 0);
    }

    @Test
    void testGetTierMultiplier_unknownModel_returnsDefault() {
        double multiplier = ProviderCostCatalog.getTierMultiplier("unknown-model");
        assertEquals(1.0, multiplier);
    }

    @Test
    void testEstimateCost_returnsValidCost() {
        double cost = ProviderCostCatalog.estimateCost("groq", "llama-3.3-70b-versatile", 1000, 500);
        assertTrue(cost >= 0);
    }

    @Test
    void testGetCheapestModel_returnsNonNull() {
        String cheapest = ProviderCostCatalog.getCheapestModel("groq", false);
        assertNotNull(cheapest);
    }

    @Test
    void testGetCheapestModel_unknownProvider_returnsCheapestAvailable() {
        String cheapest = ProviderCostCatalog.getCheapestModel("unknown", false);
        assertNotNull(cheapest);
    }
}