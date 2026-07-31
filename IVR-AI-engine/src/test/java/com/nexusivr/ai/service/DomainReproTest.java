package com.nexusivr.ai.service;

public class DomainReproTest {
    public static void main(String[] args) {
        String[] prompts = {
            "Create a university helpdesk IVR for student support, admissions, and financial aid inquiries.",
            "Create a secure banking IVR system for account balance, credit cards, and loan inquiries.",
            "Create a telecom customer support IVR for billing, roaming, and broadband issues.",
            "Create a pizza restaurant IVR for orders and reservations. Include departments for Takeout Orders, Reservations, and Hostess."
        };
        String[] expected = {"education", "banking", "telecom", "restaurant"};
        
        for (int i = 0; i < prompts.length; i++) {
            String result = DomainDetector.detect(prompts[i]);
            String status = result.equals(expected[i]) ? "OK" : "WRONG";
            System.out.println(status + " | Expected: " + expected[i] + " | Got: " + result + " | Prompt: " + prompts[i].substring(0, Math.min(60, prompts[i].length())) + "...");
        }
    }
}
