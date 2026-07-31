package com.nexusivr.ai.service;

import com.nexusivr.ai.model.AiAgent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of available AI Agents in the NexusIVR platform.
 */
public class AiAgentRegistry {
    private static final Map<String, AiAgent> AGENTS = new LinkedHashMap<>();

    static {
        // 1. AI Assistant (General Purpose)
        AGENTS.put("assistant", new AiAgent(
                "assistant",
                "AI Assistant (General Purpose)",
                "Conversational assistant for any general inquiries.",
                "You are a helpful, general-purpose AI assistant. Assist the user with any queries.",
                List.of("Help me plan my project", "What can you do?", "Write a greeting message")
        ));

        // 2. IVR Flow Generator
        AGENTS.put("generator", new AiAgent(
                "generator",
                "IVR Flow Generator",
                "Specialist in generating brand-new call flows.",
                "You are an expert IVR Flow Generator. Generate logical, clean, valid IVR flow definitions in JSON format.",
                List.of("🏦 Design a banking IVR", "🏨 Create a hotel concierge IVR", "🏥 Hospital appointment booking", "📞 Telecom customer support")
        ));

        // 3. IVR Flow Optimizer
        AGENTS.put("optimizer", new AiAgent(
                "optimizer",
                "IVR Flow Optimizer",
                "Specialist in modifying and optimizing existing call flows.",
                "You are an expert IVR Flow Optimizer. Analyze the existing IVR flow and optimize it for low latency, high call containment, and clear routing. Respond only with valid JSON. Modify existing flows, preserve node IDs.",
                List.of("Simplify the flow", "Reduce node count", "Improve caller experience", "Add error handling")
        ));

        // 4. IVR Validator
        AGENTS.put("validator", new AiAgent(
                "validator",
                "IVR Validator",
                "Analyzes structure and identifies flaws in flows.",
                "You are an expert IVR Flow Validator. Check the flow for validation issues, orphan nodes, invalid routing, or missing prompts.",
                List.of("Check routing validity", "Check voice prompts", "Identify dead ends")
        ));

        // 5. Telecom Expert
        AGENTS.put("telecom", new AiAgent(
                "telecom",
                "Telecom Expert",
                "Domain expert in telecom customer care and routing.",
                "You are an expert telecom IVR designer. Design telecom billing, mobile plans, roaming, and SIM activation flows.",
                List.of("Mobile Plan Billing", "Roaming Activation", "SIM Swap Service")
        ));

        // 6. Banking Expert
        AGENTS.put("banking", new AiAgent(
                "banking",
                "Banking Expert",
                "Domain expert in retail banking, cards, and loan support.",
                "You are an expert banking IVR designer. Design flows for loan status inquiries, credit card activation, fraud report hotlines, and balance checks with secure routing.",
                List.of("Loan Status", "Credit Card Support", "Fraud Hotline", "Balance Inquiry")
        ));

        // 7. Healthcare Expert
        AGENTS.put("healthcare", new AiAgent(
                "healthcare",
                "Healthcare Expert",
                "Domain expert in appointments, billing, and triage routing.",
                "You are an expert healthcare IVR designer. Design flows for doctor appointments, emergency routing, pharmacy/prescription refills, and insurance verification.",
                List.of("Appointment Booking", "Emergency Routing", "Pharmacy Refill", "Insurance Verification")
        ));

        // 8. Hospitality Expert
        AGENTS.put("hospitality", new AiAgent(
                "hospitality",
                "Hospitality Expert",
                "Domain expert in hotel booking, concierge, and room service.",
                "You are an expert hospitality IVR designer. Design concierge, room service, front desk, housekeeping, and restaurant reservation flows.",
                List.of("Room Service", "Reservation", "Front Desk", "Housekeeping")
        ));

        // 9. Customer Support Expert
        AGENTS.put("support", new AiAgent(
                "support",
                "Customer Support Expert",
                "Specialist in support ticket handling and agent queues.",
                "You are an expert customer support IVR designer. Optimize customer routing, agent escalation, feedback loops, and query tiering.",
                List.of("Agent Escalation", "Query Tiering", "Customer Feedback")
        ));

        // 10. Restaurant & Food Service Expert (Fix 9b: was incorrectly mapped to hospitality/hotel agent)
        AGENTS.put("restaurant", new AiAgent(
                "restaurant",
                "Restaurant & Food Service Expert",
                "Domain expert in order placement, delivery, reservations, and kitchen routing.",
                "You are an expert restaurant and food-service IVR designer. Design flows for order placement, delivery status tracking, table reservations, catering inquiries, and kitchen/manager routing. Use caller-friendly prompts suited for a fast-food, casual dining, or fine-dining context.",
                List.of("Place an Order", "Track Delivery", "Table Reservation", "Catering Inquiry")
        ));

        // 11. Higher Education Expert (Fix 9b: new domain for university/campus/helpline requests)
        AGENTS.put("education", new AiAgent(
                "education",
                "Higher Education Expert",
                "Domain expert in admissions, financial aid, student services, and campus emergency routing.",
                "You are an expert higher-education IVR designer. Design flows for admissions inquiries, financial aid and scholarship support, student services (registration, transcripts, ID cards), campus emergency and security routing, faculty and registrar lines, and library or academic department transfers. Use professional, campus-appropriate language.",
                List.of("Admissions Inquiry", "Financial Aid Support", "Campus Emergency", "Student Registration")
        ));

        // 12. Insurance Expert (Bug C: was missing — requests like "insurance claims IVR" received no expert context)
        AGENTS.put("insurance", new AiAgent(
                "insurance",
                "Insurance Expert",
                "Domain expert in claims filing, policy inquiries, payments, and adjuster transfers.",
                "You are an expert insurance IVR designer. Design flows for claims filing, policy detail inquiries, premium payment processing, coverage verification, fraud reporting, and transfer to claims adjusters or billing departments. Always prioritize clear policy number collection, customer authentication, and calm, reassuring language.",
                List.of("File a Claim", "Policy Inquiry", "Premium Payment", "Speak to Adjuster")
        ));

        // 13. Airline Expert (Bug C: was missing)
        AGENTS.put("airline", new AiAgent(
                "airline",
                "Airline Expert",
                "Domain expert in flight status, bookings, baggage, and rebooking flows.",
                "You are an expert airline IVR designer. Design flows for flight status checks, booking and rebooking support, baggage claim status, check-in assistance, frequent flyer inquiries, and agent transfers. Support confirmation code collection via DTMF input. Use calm, professional, travel-appropriate language and always provide emergency escalation paths.",
                List.of("Flight Status", "New Booking", "Baggage Claim", "Speak to Agent")
        ));

        // 14. Retail / E-Commerce Expert (Bug C: was missing)
        AGENTS.put("retail", new AiAgent(
                "retail",
                "Retail & E-Commerce Expert",
                "Domain expert in order tracking, returns, refunds, and customer support for retail stores.",
                "You are an expert retail and e-commerce IVR designer. Design flows for order status and tracking, returns and refund processing, store locations and hours, loyalty program support, and escalation to customer service agents. Support order number collection via DTMF input. Use friendly, brand-consistent language and provide clear self-service options to minimize agent transfers.",
                List.of("Track My Order", "Return & Refund", "Store Locations", "Customer Support")
        ));

        // 15. Government / Citizen Services Expert (Bug C: was missing)
        AGENTS.put("government", new AiAgent(
                "government",
                "Government & Citizen Services Expert",
                "Domain expert in citizen services, permits, tax support, and municipal department routing.",
                "You are an expert government and citizen services IVR designer. Design flows for permit applications, tax information and payment, public records requests, department directory navigation, appointment booking, and emergency service routing. Use formal, accessible, and inclusive language. Ensure business hours checks and after-hours messaging. Comply with accessibility standards (plain language, alternative contact options).",
                List.of("Business Permits", "Tax Information", "Public Records", "Citizen Helpdesk")
        ));
    }

    public static List<AiAgent> getAllAgents() {
        return new ArrayList<>(AGENTS.values());
    }

    public static AiAgent getAgent(String id) {
        if (id == null) return null;
        return AGENTS.get(id.toLowerCase().trim());
    }
}
