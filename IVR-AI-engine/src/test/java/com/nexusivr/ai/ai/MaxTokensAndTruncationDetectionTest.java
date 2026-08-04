package com.nexusivr.ai.ai;

import com.nexusivr.ai.config.GlobalAiConfig;
import com.nexusivr.ai.config.LlmConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MaxTokensAndTruncationDetectionTest {

    @Test
    @DisplayName("LlmConfig and GlobalAiConfig default maxTokens is at least 8192 to prevent complex flow truncation")
    void testMaxTokensConfigurationIsAtLeast8192() {
        int maxTokens = LlmConfig.getMaxTokens();
        assertTrue(maxTokens >= 8192, "LlmConfig.getMaxTokens() must be at least 8192, but was: " + maxTokens);

        GlobalAiConfig.getInstance().resetToDefaults();
        int globalMaxTokens = GlobalAiConfig.getInstance().getMaxTokens();
        assertTrue(globalMaxTokens >= 8192, "GlobalAiConfig.getMaxTokens() must be at least 8192, but was: " + globalMaxTokens);
    }
}
