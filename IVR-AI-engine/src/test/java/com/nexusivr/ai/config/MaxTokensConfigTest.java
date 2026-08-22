package com.nexusivr.ai.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MaxTokensConfigTest {

    @Test
    public void testMaxTokensMinimumThreshold() {
        int lmt = LlmConfig.getMaxTokens();
        assertTrue(lmt >= 8192, "LlmConfig.getMaxTokens() must be at least 8192 to accommodate reasoning tokens");

        GlobalAiConfig config = GlobalAiConfig.getInstance();
        config.resetToDefaults();
        int gmt = config.getMaxTokens();
        assertTrue(gmt >= 8192, "GlobalAiConfig.getMaxTokens() must be at least 8192 to prevent generation truncation");
    }
}
