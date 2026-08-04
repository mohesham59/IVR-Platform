package com.nexusivr.ai.ai;

import com.nexusivr.ai.service.DepartmentExtractor;
import com.nexusivr.ai.service.DomainFlowGenerator;
import com.nexusivr.ai.service.exception.ProviderException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TemplateGeneratorDynamicFallbackTest {

    @Test
    void testDepartmentExtractorExtractsNumberedAndKeywordLists() {
        List<String> depts1 = DepartmentExtractor.extractDepartments("Create an IVR with departments for Reservations, Front Desk, and Room Service");
        assertEquals(3, depts1.size());
        assertTrue(depts1.contains("Reservations"));
        assertTrue(depts1.contains("Front Desk"));
        assertTrue(depts1.contains("Room Service"));

        List<String> depts2 = DepartmentExtractor.extractDepartments("Press 1 for Sales, 2 for Shipping, 3 for Returns");
        assertEquals(3, depts2.size());
        assertTrue(depts2.contains("Sales"));
        assertTrue(depts2.contains("Shipping"));
        assertTrue(depts2.contains("Returns"));
    }

    @Test
    void testDomainFlowGeneratorUsesExtractedDepartments() {
        DomainFlowGenerator generator = new DomainFlowGenerator();
        String prompt = "Design an IVR for Acme Corp with departments for Sales, Shipping, and Returns";
        String vxml = generator.generateVxml("generic", prompt);

        assertNotNull(vxml);
        assertTrue(vxml.contains("Sales"));
        assertTrue(vxml.contains("Shipping"));
        assertTrue(vxml.contains("Returns"));
        assertFalse(vxml.contains("takeout order"), "Custom fallback must not use generic restaurant placeholders");
    }

    @Test
    void testBankingAndTelecomPromptsProduceTailoredDepartmentsNotGenericPlaceholders() {
        DomainFlowGenerator generator = new DomainFlowGenerator();

        String bankingPrompt = "design the banking IVR with departments for Billing, Cards, Loans, and Agent";
        String bankingVxml = generator.generateVxml("banking", bankingPrompt);
        assertNotNull(bankingVxml);
        assertTrue(bankingVxml.contains("billing"), "Banking fallback VXML must contain 'billing' form ID");
        assertTrue(bankingVxml.contains("cards"), "Banking fallback VXML must contain 'cards' form ID");
        assertTrue(bankingVxml.contains("loans"), "Banking fallback VXML must contain 'loans' form ID");
        assertTrue(bankingVxml.contains("agent"), "Banking fallback VXML must contain 'agent' form ID");
        assertFalse(bankingVxml.contains("dept_1"), "Banking fallback VXML must not use hardcoded 'dept_1'");

        String telecomPrompt = "design the telecom IVR with departments for Billing, Roaming, SIM Support, and Broadband";
        String telecomVxml = generator.generateVxml("telecom", telecomPrompt);
        assertNotNull(telecomVxml);
        assertTrue(telecomVxml.contains("billing"), "Telecom fallback VXML must contain 'billing' form ID");
        assertTrue(telecomVxml.contains("roaming"), "Telecom fallback VXML must contain 'roaming' form ID");
        assertTrue(telecomVxml.contains("sim_support"), "Telecom fallback VXML must contain 'sim_support' form ID");
        assertTrue(telecomVxml.contains("broadband"), "Telecom fallback VXML must contain 'broadband' form ID");
        assertFalse(telecomVxml.contains("dept_1"), "Telecom fallback VXML must not use hardcoded 'dept_1'");
    }

    @Test
    void testExactEvidencePromptsExtractionCleanlyWithoutTrailingDigitsOrSkippedDepartments() {
        // 1. Telecom prompt
        String telecomPrompt = "Create a telecom customer support IVR. Include departments for Billing, Roaming, SIM Support, and Broadband... main menu with options 1-Billing 2-Roaming 3-SIM Support 4-Internet 0-Specialist";
        List<String> telecomDepts = DepartmentExtractor.extractDepartments(telecomPrompt);
        assertEquals(4, telecomDepts.size(), "Telecom prompt must extract all 4 departments cleanly");
        assertTrue(telecomDepts.contains("Billing"));
        assertTrue(telecomDepts.contains("Roaming"));
        assertTrue(telecomDepts.contains("Sim Support"));
        assertTrue(telecomDepts.contains("Broadband"));
        assertFalse(telecomDepts.contains("Billing 2"), "Must not leak trailing digits into department name");
        assertFalse(telecomDepts.contains("Sim Support 4"), "Must not leak trailing digits into department name");

        // 2. Pizza restaurant prompt
        String pizzaPrompt = "Create a pizza restaurant IVR for orders and reservations. Include departments for Takeout Orders, Reservations, and Hostess... main menu with options 1-Takeout Orders 2-Reservations 3-Hours & Location 0-Hostess";
        List<String> pizzaDepts = DepartmentExtractor.extractDepartments(pizzaPrompt);
        assertEquals(3, pizzaDepts.size(), "Pizza prompt must extract all 3 departments cleanly");
        assertTrue(pizzaDepts.contains("Takeout Orders"));
        assertTrue(pizzaDepts.contains("Reservations"));
        assertTrue(pizzaDepts.contains("Hostess"));
        assertFalse(pizzaDepts.contains("Takeout Orders 2"), "Must not leak trailing digits into department name");

        // 3. Hospital prompt
        String hospitalPrompt = "Create a comprehensive hospital IVR system... Include departments for Appointments, Pharmacy, Billing, and Triage... direct caller options 1-Appointments 2-Pharmacy 3-Billing 4-Triage";
        List<String> hospitalDepts = DepartmentExtractor.extractDepartments(hospitalPrompt);
        assertEquals(4, hospitalDepts.size(), "Hospital prompt must extract all 4 departments cleanly");
        assertTrue(hospitalDepts.contains("Appointments"));
        assertTrue(hospitalDepts.contains("Pharmacy"));
        assertTrue(hospitalDepts.contains("Billing"));
        assertTrue(hospitalDepts.contains("Triage"));
        assertFalse(hospitalDepts.contains("Appointments 2"), "Must not leak trailing digits into department name");
    }

    @Test
    void testDomainFlowGeneratorGeneratesAdaptiveVxmlWhenDomainIsGenericAndNoDepartmentsExtracted() {
        DomainFlowGenerator generator = new DomainFlowGenerator();
        String prompt = "design an IVR for XYZ Widget";

        String vxml = generator.generateVxml("generic", prompt);
        assertNotNull(vxml);
        assertTrue(vxml.contains("<vxml"), "Must generate domain-adaptive VXML when domain is generic and no departments are provided");
    }
}
