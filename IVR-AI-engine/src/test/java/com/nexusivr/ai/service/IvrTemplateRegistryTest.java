package com.nexusivr.ai.service;

import com.nexusivr.ai.model.IvrTemplate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class IvrTemplateRegistryTest {

    @Test
    public void testRegistryLoading() {
        List<IvrTemplate> all = IvrTemplateRegistry.getAllTemplates();
        assertEquals(11, all.size()); // 9 original + education (Fix 9b) + generic (Fix 9b)

        for (IvrTemplate temp : all) {
            assertNotNull(temp.getDomainName());
            assertNotNull(temp.getDescription());
            assertNotNull(temp.getRecommendedNodeOrder());
            assertNotNull(temp.getStandardRouting());
            assertNotNull(temp.getCommonMenuLayout());
            assertNotNull(temp.getRecommendedQueues());
            assertNotNull(temp.getTypicalTransfers());
            assertNotNull(temp.getCommonGreetings());
            assertNotNull(temp.getBusinessHoursPlacement());
            assertNotNull(temp.getErrorHandling());
            assertNotNull(temp.getTimeoutHandling());
            assertNotNull(temp.getTemplateFlowJson());
            assertTrue(temp.getTemplateFlowJson().contains("nodes"));
        }
    }

    @Test
    public void testClosestTemplateMatching() {
        // Direct matching
        IvrTemplate bank = IvrTemplateRegistry.getClosestTemplate("banking");
        assertEquals("Banking", bank.getDomainName());

        IvrTemplate retail = IvrTemplateRegistry.getClosestTemplate("retail store support");
        assertEquals("Retail", retail.getDomainName());

        // Semantic fallbacks
        IvrTemplate finance = IvrTemplateRegistry.getClosestTemplate("finance and loans");
        assertEquals("Banking", finance.getDomainName());

        IvrTemplate emergency = IvrTemplateRegistry.getClosestTemplate("medical triage clinic");
        assertEquals("Healthcare", emergency.getDomainName());

        IvrTemplate checkin = IvrTemplateRegistry.getClosestTemplate("hotel check-in flow");
        assertEquals("Hospitality", checkin.getDomainName());

        // Fix 9b: education keywords should resolve to Education template
        IvrTemplate university = IvrTemplateRegistry.getClosestTemplate("university helpline for admissions and financial aid");
        assertEquals("Education", university.getDomainName());

        // Fix 9b: Default fallback is now Generic (neutral), not Hospitality
        IvrTemplate def = IvrTemplateRegistry.getClosestTemplate("custom undefined keyword flow");
        assertEquals("Generic", def.getDomainName());
    }
}
