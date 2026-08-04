package com.nexusivr.ai.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PromptCompletenessCheckerTest {

    @Test
    void testWellSpecifiedPromptWithNumberedMenu() {
        String prompt = "Create a restaurant IVR with menu options: Press 1 for takeout orders, Press 2 for reservations, Press 3 for hours. Include a greeting and closing message.";
        assertTrue(PromptCompletenessChecker.isWellSpecified(prompt));
    }

    @Test
    void testWellSpecifiedPromptWithDepartments() {
        String prompt = "Create a banking IVR. Departments include Billing, Cards, and Loans. Press 1 for balance, Press 2 for cards, Press 3 for loans. Include greeting and goodbye.";
        assertTrue(PromptCompletenessChecker.isWellSpecified(prompt));
    }

    @Test
    void testVaguePromptTriggersRefinement() {
        String prompt = "Create a bakery IVR";
        assertFalse(PromptCompletenessChecker.isWellSpecified(prompt));
    }

    @Test
    void testVeryShortPromptTriggersRefinement() {
        String prompt = "make me an IVR";
        assertFalse(PromptCompletenessChecker.isWellSpecified(prompt));
    }

    @Test
    void testLongPromptWithElementsIsWellSpecified() {
        String prompt = "Create a comprehensive healthcare IVR. Press 1 for appointments, Press 2 for pharmacy, Press 3 for billing, Press 4 for triage. Include greeting, business hours, error handling, and closing message.";
        assertTrue(PromptCompletenessChecker.isWellSpecified(prompt));
    }
}
