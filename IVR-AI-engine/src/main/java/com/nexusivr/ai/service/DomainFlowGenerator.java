package com.nexusivr.ai.service;

import java.util.List;
import java.util.UUID;

/**
 * Generates domain-specific VoiceXML 2.1 flows used as the ultimate fallback
 * when every AI provider is unavailable.
 * <p>
 * Each generator produces a complete, valid VXML document that the existing
 * {@link VxmlParser} and {@link VxmlToFlowConverter} pipeline can translate
 * into the standard builder {@code nodes}/{@code edges} JSON.
 */
public class DomainFlowGenerator {

    public String generateVxml(String domain, String description) {
        String activeDomain = domain;
        if (activeDomain == null || activeDomain.isBlank()) {
            activeDomain = DomainDetector.detect(description);
        }

        List<String> extractedDepts = DepartmentExtractor.extractDepartments(description);
        boolean isGeneric = activeDomain == null || activeDomain.isBlank() || "generic".equalsIgnoreCase(activeDomain.trim());

        // Requirement 2: If domain detection failed ('generic') AND department extraction yields NOTHING usable, throw error
        if (isGeneric && extractedDepts.isEmpty()) {
            throw new com.nexusivr.ai.service.exception.ProviderException(
                    "template-generator",
                    "I couldn't reach any AI provider and don't have enough information to build a fallback for this request. Please try again shortly, or provide more details (departments, menu options) so I can build something without AI assistance.",
                    com.nexusivr.ai.service.exception.ProviderException.FailureReason.PROVIDER_ERROR
            );
        }

        // Requirement 1a & 1b: If custom extracted departments exist, build menu options and node names using THOSE terms
        if (!extractedDepts.isEmpty()) {
            return generateCustomDepartmentsVxml(activeDomain, description, extractedDepts);
        }

        return switch (activeDomain.toLowerCase().trim()) {
            case "restaurant", "pizza", "dining", "food", "cafe", "bistro", "bakery" -> generateRestaurantVxml(description);
            case "hotel", "hospitality", "resort", "lodging", "motel", "inn" -> generateHotelVxml(description);
            case "banking", "bank", "finance", "financial" -> generateBankingVxml(description);
            case "healthcare", "hospital", "clinic", "medical", "doctor", "health" -> generateHealthcareVxml(description);
            case "education", "school", "university", "college", "campus", "academic" -> generateEducationVxml(description);
            case "airline", "flight", "travel", "airport" -> generateAirlineVxml(description);
            case "insurance", "claim", "policy", "coverage" -> generateInsuranceVxml(description);
            case "retail", "store", "shop", "e_commerce", "ecommerce", "shopping" -> generateRetailVxml(description);
            case "government", "municipal", "city", "dmv", "public_service" -> generateGovernmentVxml(description);
            case "telecom", "mobile", "cellular", "broadband", "phone" -> generateTelecomVxml(description);
            case "technical_support", "technical support", "tech support", "it support", "helpdesk" -> generateTechnicalSupportVxml(description);
            default -> generateGenericDomainAdaptiveVxml(description);
        };
    }

    private String generateCustomDepartmentsVxml(String domain, String description, List<String> departments) {
        String name = cleanBusinessName(domain, description);
        StringBuilder vxml = new StringBuilder();
        vxml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        vxml.append("<vxml version=\"2.1\" xmlns=\"http://www.w3.org/2001/vxml\">\n");

        // Start form with greeting
        vxml.append("  <form id=\"start\">\n");
        vxml.append("    <block>\n");
        vxml.append("      <prompt>Welcome to ").append(name).append(". ");
        for (int i = 0; i < departments.size(); i++) {
            vxml.append("Press ").append(i + 1).append(" for ").append(escape(departments.get(i))).append(". ");
        }
        vxml.append("Press 0 to speak to an agent.</prompt>\n");
        vxml.append("      <goto next=\"#menu\"/>\n");
        vxml.append("    </block>\n");
        vxml.append("  </form>\n");

        // Menu form
        vxml.append("  <form id=\"menu\">\n");
        vxml.append("    <menu>\n");
        vxml.append("      <prompt>Please select an option.</prompt>\n");
        for (int i = 0; i < departments.size(); i++) {
            String formId = sanitizeFormId(departments.get(i), i);
            vxml.append("      <choice accept=\"digits ").append(i + 1).append("\" next=\"#").append(formId).append("\"/>\n");
        }
        vxml.append("      <choice accept=\"digits 0\" next=\"#agent\"/>\n");
        vxml.append("    </menu>\n");
        vxml.append("  </form>\n");

        // Forms for each department
        for (int i = 0; i < departments.size(); i++) {
            String deptName = escape(departments.get(i));
            String formId = sanitizeFormId(departments.get(i), i);
            vxml.append("  <form id=\"").append(formId).append("\">\n");
            vxml.append("    <block>\n");
            vxml.append("      <prompt>Please hold while we connect you to ").append(deptName).append(".</prompt>\n");
            vxml.append("      <transfer dest=\"+").append(1001 + i).append("\"/>\n");
            vxml.append("    </block>\n");
            vxml.append("  </form>\n");
        }

        // Agent form
        vxml.append("  <form id=\"agent\">\n");
        vxml.append("    <block>\n");
        vxml.append("      <prompt>Please hold while we connect you to a representative.</prompt>\n");
        vxml.append("      <transfer dest=\"+0\"/>\n");
        vxml.append("    </block>\n");
        vxml.append("  </form>\n");

        vxml.append("</vxml>");
        return vxml.toString();
    }

    private String sanitizeFormId(String deptName, int index) {
        if (deptName == null || deptName.isBlank()) return "dept_" + (index + 1);
        String id = deptName.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        return id.isBlank() ? "dept_" + (index + 1) : id;
    }

    private String cleanBusinessName(String domain, String description) {
        if (domain != null && !domain.isBlank() && !"generic".equalsIgnoreCase(domain)) {
            String d = domain.trim().replaceAll("[-_]", " ");
            return Character.toUpperCase(d.charAt(0)) + d.substring(1) + " Services";
        }
        return "our Customer Services";
    }

    private String generateRestaurantVxml(String description) {
        String name = escape(description != null ? description : "Restaurant IVR");
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <vxml version="2.1" xmlns="http://www.w3.org/2001/vxml">
                  <form id="start">
                    <block>
                      <prompt>Welcome to %s. Press 1 to place a takeout order. Press 2 for reservations. Press 3 for hours and location. Press 0 to speak to our hostess.</prompt>
                      <goto next="#menu"/>
                    </block>
                  </form>
                  <form id="menu">
                    <menu>
                      <prompt>Please select an option.</prompt>
                      <choice accept="digits 1" next="#order"/>
                      <choice accept="digits 2" next="#reservation"/>
                      <choice accept="digits 3" next="#info"/>
                      <choice accept="digits 0" next="#hostess"/>
                    </menu>
                  </form>
                  <form id="order">
                    <block>
                      <prompt>Please hold while we connect you to our takeout order line.</prompt>
                      <transfer dest="+1001"/>
                    </block>
                  </form>
                  <form id="reservation">
                    <block>
                      <prompt>Please hold while we connect you to our reservation desk.</prompt>
                      <transfer dest="+1002"/>
                    </block>
                  </form>
                  <form id="info">
                    <block>
                      <prompt>We are located at 123 Main Street. Our hours are Monday through Friday 11am to 10pm, Saturday and Sunday 10am to 11pm. Thank you for calling.</prompt>
                      <disconnect/>
                    </block>
                  </form>
                  <form id="hostess">
                    <block>
                      <prompt>Please hold while we connect you to our hostess station.</prompt>
                      <transfer dest="+1003"/>
                    </block>
                  </form>
                </vxml>
                """.formatted(name);
    }

    private String generateHotelVxml(String description) {
        String name = escape(description != null ? description : "Hotel IVR");
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <vxml version="2.1" xmlns="http://www.w3.org/2001/vxml">
                  <form id="start">
                    <block>
                      <prompt>Welcome to %s. Press 1 for room reservations. Press 2 for front desk. Press 3 for room service. Press 4 for housekeeping. Press 0 to speak to the concierge.</prompt>
                      <goto next="#menu"/>
                    </block>
                  </form>
                  <form id="menu">
                    <menu>
                      <prompt>How may we assist you?</prompt>
                      <choice accept="digits 1" next="#reservations"/>
                      <choice accept="digits 2" next="#front_desk"/>
                      <choice accept="digits 3" next="#room_service"/>
                      <choice accept="digits 4" next="#housekeeping"/>
                      <choice accept="digits 0" next="#concierge"/>
                    </menu>
                  </form>
                  <form id="reservations">
                    <block>
                      <prompt>Please hold while we connect you to our reservations team.</prompt>
                      <transfer dest="+2001"/>
                    </block>
                  </form>
                  <form id="front_desk">
                    <block>
                      <prompt>Please hold while we connect you to the front desk.</prompt>
                      <transfer dest="+2002"/>
                    </block>
                  </form>
                  <form id="room_service">
                    <block>
                      <prompt>Please hold while we connect you to room service.</prompt>
                      <transfer dest="+2003"/>
                    </block>
                  </form>
                  <form id="housekeeping">
                    <block>
                      <prompt>Please hold while we connect you to housekeeping.</prompt>
                      <transfer dest="+2004"/>
                    </block>
                  </form>
                  <form id="concierge">
                    <block>
                      <prompt>Please hold while we connect you to our concierge.</prompt>
                      <transfer dest="+2005"/>
                    </block>
                  </form>
                </vxml>
                """.formatted(name);
    }

    private String generateBankingVxml(String description) {
        String name = escape(description != null ? description : "Banking IVR");
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <vxml version="2.1" xmlns="http://www.w3.org/2001/vxml">
                  <form id="start">
                    <block>
                      <prompt>Welcome to %s. For your security, please enter your 16 digit card or account number followed by pound.</prompt>
                      <goto next="#authenticate"/>
                    </block>
                  </form>
                  <form id="authenticate">
                    <field name="account">
                      <prompt>Please enter your 16 digit card or account number followed by pound.</prompt>
                      <grammar mode="dtmf" version="1.0">
                        <rule id="digits">
                          <one-of>
                            <item>0</item>
                            <item>1</item>
                            <item>2</item>
                            <item>3</item>
                            <item>4</item>
                            <item>5</item>
                            <item>6</item>
                            <item>7</item>
                            <item>8</item>
                            <item>9</item>
                          </one-of>
                        </rule>
                      </grammar>
                      <filled>
                        <prompt>Thank you. Please enter your 4 digit PIN followed by pound.</prompt>
                        <goto next="#menu"/>
                      </filled>
                      <noinput>
                        <prompt>We did not receive any input. Please try again.</prompt>
                        <goto next="#authenticate"/>
                      </noinput>
                      <nomatch>
                        <prompt>That is not a valid digit. Please try again.</prompt>
                        <goto next="#authenticate"/>
                      </nomatch>
                    </field>
                  </form>
                  <form id="menu">
                    <menu>
                      <prompt>Press 1 for account balance. Press 2 for credit card services. Press 3 for loan status. Press 4 to speak to an agent. Press 0 to end this call.</prompt>
                      <choice accept="digits 1" next="#balance"/>
                      <choice accept="digits 2" next="#cards"/>
                      <choice accept="digits 3" next="#loans"/>
                      <choice accept="digits 4" next="#agent"/>
                      <choice accept="digits 0" next="#end"/>
                    </menu>
                  </form>
                  <form id="balance">
                    <block>
                      <prompt>Your available balance is 1,234 dollars and 56 cents. Thank you for banking with us.</prompt>
                      <disconnect/>
                    </block>
                  </form>
                  <form id="cards">
                    <block>
                      <prompt>Please hold while we connect you to card services.</prompt>
                      <transfer dest="+3002"/>
                    </block>
                  </form>
                  <form id="loans">
                    <block>
                      <prompt>Please hold while we connect you to loan servicing.</prompt>
                      <transfer dest="+3003"/>
                    </block>
                  </form>
                  <form id="agent">
                    <block>
                      <prompt>Please hold while we connect you to a live agent.</prompt>
                      <transfer dest="+3004"/>
                    </block>
                  </form>
                  <form id="end">
                    <block>
                      <prompt>Thank you for calling. Goodbye.</prompt>
                      <disconnect/>
                    </block>
                  </form>
                </vxml>
                """.formatted(name);
    }

    private String generateHealthcareVxml(String description) {
        String name = escape(description != null ? description : "Healthcare IVR");
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <vxml version="2.1" xmlns="http://www.w3.org/2001/vxml">
                  <form id="start">
                    <block>
                      <prompt>Thank you for calling %s. If you are experiencing a life threatening emergency, please hang up and dial 911. Press 1 to schedule an appointment. Press 2 for pharmacy and refills. Press 3 for billing. Press 4 for emergency triage. Press 0 to speak to the nurse line.</prompt>
                      <goto next="#menu"/>
                    </block>
                  </form>
                  <form id="menu">
                    <menu>
                      <prompt>Please select an option.</prompt>
                      <choice accept="digits 1" next="#appointments"/>
                      <choice accept="digits 2" next="#pharmacy"/>
                      <choice accept="digits 3" next="#billing"/>
                      <choice accept="digits 4" next="#triage"/>
                      <choice accept="digits 0" next="#nurse"/>
                    </menu>
                  </form>
                  <form id="appointments">
                    <block>
                      <prompt>Please hold while we connect you to our appointment scheduling line.</prompt>
                      <transfer dest="+4001"/>
                    </block>
                  </form>
                  <form id="pharmacy">
                    <block>
                      <prompt>Please hold while we connect you to the pharmacy refill line.</prompt>
                      <transfer dest="+4002"/>
                    </block>
                  </form>
                  <form id="billing">
                    <block>
                      <prompt>Please hold while we connect you to the billing department.</prompt>
                      <transfer dest="+4003"/>
                    </block>
                  </form>
                  <form id="triage">
                    <block>
                      <prompt>Please hold while we connect you to the emergency triage nurse.</prompt>
                      <transfer dest="+4004"/>
                    </block>
                  </form>
                  <form id="nurse">
                    <block>
                      <prompt>Please hold while we connect you to the nurse line.</prompt>
                      <transfer dest="+4005"/>
                    </block>
                  </form>
                </vxml>
                """.formatted(name);
    }

    private String generateInsuranceVxml(String description) {
        String name = escape(description != null ? description : "Insurance IVR");
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <vxml version="2.1" xmlns="http://www.w3.org/2001/vxml">
                  <form id="start">
                    <block>
                      <prompt>Welcome to %s. Please enter your 8 digit policy number followed by pound to continue.</prompt>
                      <goto next="#menu"/>
                    </block>
                  </form>
                  <form id="menu">
                    <menu>
                      <prompt>Press 1 to file a claim. Press 2 for premium payments. Press 3 to check your policy details. Press 4 to speak to an agent. Press 0 to end this call.</prompt>
                      <choice accept="digits 1" next="#claim"/>
                      <choice accept="digits 2" next="#payment"/>
                      <choice accept="digits 3" next="#policy"/>
                      <choice accept="digits 4" next="#agent"/>
                      <choice accept="digits 0" next="#end"/>
                    </menu>
                  </form>
                  <form id="claim">
                    <block>
                      <prompt>Please hold while we connect you to claims processing.</prompt>
                      <transfer dest="+5001"/>
                    </block>
                  </form>
                  <form id="payment">
                    <block>
                      <prompt>Please hold while we connect you to billing support.</prompt>
                      <transfer dest="+5002"/>
                    </block>
                  </form>
                  <form id="policy">
                    <block>
                      <prompt>Please hold while we retrieve your policy details.</prompt>
                      <transfer dest="+5003"/>
                    </block>
                  </form>
                  <form id="agent">
                    <block>
                      <prompt>Please hold while we connect you to a licensed agent.</prompt>
                      <transfer dest="+5004"/>
                    </block>
                  </form>
                  <form id="end">
                    <block>
                      <prompt>Thank you for calling. Goodbye.</prompt>
                      <disconnect/>
                    </block>
                  </form>
                </vxml>
                """.formatted(name);
    }

    private String generateRetailVxml(String description) {
        String name = escape(description != null ? description : "Retail IVR");
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <vxml version="2.1" xmlns="http://www.w3.org/2001/vxml">
                  <form id="start">
                    <block>
                      <prompt>Welcome to %s. Please enter your order number or press star to continue.</prompt>
                      <goto next="#menu"/>
                    </block>
                  </form>
                  <form id="menu">
                    <menu>
                      <prompt>Press 1 for order status and tracking. Press 2 for returns and refunds. Press 3 for store locations and hours. Press 4 to speak to customer support. Press 0 to end this call.</prompt>
                      <choice accept="digits 1" next="#order_status"/>
                      <choice accept="digits 2" next="#returns"/>
                      <choice accept="digits 3" next="#locations"/>
                      <choice accept="digits 4" next="#support"/>
                      <choice accept="digits 0" next="#end"/>
                    </menu>
                  </form>
                  <form id="order_status">
                    <block>
                      <prompt>Please hold while we look up your order status.</prompt>
                      <transfer dest="+6001"/>
                    </block>
                  </form>
                  <form id="returns">
                    <block>
                      <prompt>Please hold while we connect you to our returns desk.</prompt>
                      <transfer dest="+6002"/>
                    </block>
                  </form>
                  <form id="locations">
                    <block>
                      <prompt>We are located at 456 Commerce Boulevard. Store hours are Monday through Saturday 9am to 9pm, Sunday 10am to 6pm.</prompt>
                      <disconnect/>
                    </block>
                  </form>
                  <form id="support">
                    <block>
                      <prompt>Please hold while we connect you to customer support.</prompt>
                      <transfer dest="+6003"/>
                    </block>
                  </form>
                  <form id="end">
                    <block>
                      <prompt>Thank you for calling. Goodbye.</prompt>
                      <disconnect/>
                    </block>
                  </form>
                </vxml>
                """.formatted(name);
    }

    private String generateGovernmentVxml(String description) {
        String name = escape(description != null ? description : "Government IVR");
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <vxml version="2.1" xmlns="http://www.w3.org/2001/vxml">
                  <form id="start">
                    <block>
                      <prompt>Thank you for contacting the %s citizen service hotline. Press 1 for business permits. Press 2 for tax information. Press 3 for office locations and hours. Press 4 for passport services. Press 0 to speak to a county clerk.</prompt>
                      <goto next="#menu"/>
                    </block>
                  </form>
                  <form id="menu">
                    <menu>
                      <prompt>Please select a service.</prompt>
                      <choice accept="digits 1" next="#permits"/>
                      <choice accept="digits 2" next="#tax"/>
                      <choice accept="digits 3" next="#locations"/>
                      <choice accept="digits 4" next="#passport"/>
                      <choice accept="digits 0" next="#clerk"/>
                    </menu>
                  </form>
                  <form id="permits">
                    <block>
                      <prompt>Please hold while we connect you to the permits department.</prompt>
                      <transfer dest="+7001"/>
                    </block>
                  </form>
                  <form id="tax">
                    <block>
                      <prompt>Please hold while we connect you to tax information.</prompt>
                      <transfer dest="+7002"/>
                    </block>
                  </form>
                  <form id="locations">
                    <block>
                      <prompt>Our main office is located at 100 Civic Center Drive. Office hours are Monday through Friday 8am to 5pm.</prompt>
                      <disconnect/>
                    </block>
                  </form>
                  <form id="passport">
                    <block>
                      <prompt>Please hold while we connect you to passport services.</prompt>
                      <transfer dest="+7003"/>
                    </block>
                  </form>
                  <form id="clerk">
                    <block>
                      <prompt>Please hold while we connect you to the county clerk.</prompt>
                      <transfer dest="+7004"/>
                    </block>
                  </form>
                </vxml>
                """.formatted(name);
    }

    private String generateTelecomVxml(String description) {
        String name = escape(description != null ? description : "Telecom IVR");
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <vxml version="2.1" xmlns="http://www.w3.org/2001/vxml">
                  <form id="start">
                    <block>
                      <prompt>Welcome to %s. Press 1 for account and billing. Press 2 for roaming services. Press 3 for SIM swap and activation. Press 4 for internet and broadband support. Press 0 to speak to a specialist.</prompt>
                      <goto next="#menu"/>
                    </block>
                  </form>
                  <form id="menu">
                    <menu>
                      <prompt>Please select an option.</prompt>
                      <choice accept="digits 1" next="#billing"/>
                      <choice accept="digits 2" next="#roaming"/>
                      <choice accept="digits 3" next="#sim"/>
                      <choice accept="digits 4" next="#broadband"/>
                      <choice accept="digits 0" next="#specialist"/>
                    </menu>
                  </form>
                  <form id="billing">
                    <block>
                      <prompt>Please hold while we connect you to billing support.</prompt>
                      <transfer dest="+8001"/>
                    </block>
                  </form>
                  <form id="roaming">
                    <block>
                      <prompt>Please hold while we connect you to roaming services.</prompt>
                      <transfer dest="+8002"/>
                    </block>
                  </form>
                  <form id="sim">
                    <block>
                      <prompt>Please hold while we connect you to SIM swap support.</prompt>
                      <transfer dest="+8003"/>
                    </block>
                  </form>
                  <form id="broadband">
                    <block>
                      <prompt>Please hold while we connect you to internet and broadband support.</prompt>
                      <transfer dest="+8004"/>
                    </block>
                  </form>
                  <form id="specialist">
                    <block>
                      <prompt>Please hold while we connect you to a specialist.</prompt>
                      <transfer dest="+8005"/>
                    </block>
                  </form>
                </vxml>
                """.formatted(name);
    }

    private String generateTechnicalSupportVxml(String description) {
        String name = escape(description != null ? description : "Technical Support IVR");
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <vxml version="2.1" xmlns="http://www.w3.org/2001/vxml">
                  <form id="start">
                    <block>
                      <prompt>Welcome to %s. Please describe your issue after the tone, or press 1 for hardware support, press 2 for software support, press 3 for network and connectivity, press 4 for account access, or press 0 to speak to a technician.</prompt>
                      <goto next="#menu"/>
                    </block>
                  </form>
                  <form id="menu">
                    <menu>
                      <prompt>Please select a support category.</prompt>
                      <choice accept="digits 1" next="#hardware"/>
                      <choice accept="digits 2" next="#software"/>
                      <choice accept="digits 3" next="#network"/>
                      <choice accept="digits 4" next="#account"/>
                      <choice accept="digits 0" next="#technician"/>
                    </menu>
                  </form>
                  <form id="hardware">
                    <block>
                      <prompt>Please hold while we connect you to hardware support.</prompt>
                      <transfer dest="+9001"/>
                    </block>
                  </form>
                  <form id="software">
                    <block>
                      <prompt>Please hold while we connect you to software support.</prompt>
                      <transfer dest="+9002"/>
                    </block>
                  </form>
                  <form id="network">
                    <block>
                      <prompt>Please hold while we connect you to network and connectivity support.</prompt>
                      <transfer dest="+9003"/>
                    </block>
                  </form>
                  <form id="account">
                    <block>
                      <prompt>Please hold while we connect you to account access support.</prompt>
                      <transfer dest="+9004"/>
                    </block>
                  </form>
                  <form id="technician">
                    <block>
                      <prompt>Please hold while we connect you to a technician.</prompt>
                      <transfer dest="+9005"/>
                    </block>
                  </form>
                </vxml>
                """.formatted(name);
    }

    private String generateEducationVxml(String description) {
        String name = escape(description != null ? description : "Educational Institution IVR");
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <vxml version="2.1" xmlns="http://www.w3.org/2001/vxml">
                  <form id="start">
                    <block>
                      <prompt>Welcome to %s. Press 1 for admissions and enrollment. Press 2 for financial aid. Press 3 for student records and transcripts. Press 4 for campus support. Press 0 to speak to the operator.</prompt>
                      <goto next="#menu"/>
                    </block>
                  </form>
                  <form id="menu">
                    <menu>
                      <prompt>Please select a department.</prompt>
                      <choice accept="digits 1" next="#admissions"/>
                      <choice accept="digits 2" next="#financial_aid"/>
                      <choice accept="digits 3" next="#records"/>
                      <choice accept="digits 4" next="#support"/>
                      <choice accept="digits 0" next="#operator"/>
                    </menu>
                  </form>
                  <form id="admissions">
                    <block>
                      <prompt>Please hold while we connect you to admissions and enrollment.</prompt>
                      <transfer dest="+4101"/>
                    </block>
                  </form>
                  <form id="financial_aid">
                    <block>
                      <prompt>Please hold while we connect you to financial aid.</prompt>
                      <transfer dest="+4102"/>
                    </block>
                  </form>
                  <form id="records">
                    <block>
                      <prompt>Please hold while we connect you to student records and transcripts.</prompt>
                      <transfer dest="+4103"/>
                    </block>
                  </form>
                  <form id="support">
                    <block>
                      <prompt>Please hold while we connect you to campus support.</prompt>
                      <transfer dest="+4104"/>
                    </block>
                  </form>
                  <form id="operator">
                    <block>
                      <prompt>Please hold while we connect you to the campus operator.</prompt>
                      <transfer dest="+4105"/>
                    </block>
                  </form>
                </vxml>
                """.formatted(name);
    }

    private String generateAirlineVxml(String description) {
        String name = escape(description != null ? description : "Airline IVR");
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <vxml version="2.1" xmlns="http://www.w3.org/2001/vxml">
                  <form id="start">
                    <block>
                      <prompt>Welcome to %s. Press 1 for flight status and schedules. Press 2 for reservations and bookings. Press 3 for baggage services. Press 4 for frequent flyer account. Press 0 to speak to a travel representative.</prompt>
                      <goto next="#menu"/>
                    </block>
                  </form>
                  <form id="menu">
                    <menu>
                      <prompt>Please select an option.</prompt>
                      <choice accept="digits 1" next="#flight_status"/>
                      <choice accept="digits 2" next="#bookings"/>
                      <choice accept="digits 3" next="#baggage"/>
                      <choice accept="digits 4" next="#frequent_flyer"/>
                      <choice accept="digits 0" next="#representative"/>
                    </menu>
                  </form>
                  <form id="flight_status">
                    <block>
                      <prompt>Please hold while we check your flight status.</prompt>
                      <transfer dest="+4201"/>
                    </block>
                  </form>
                  <form id="bookings">
                    <block>
                      <prompt>Please hold while we connect you to reservations and bookings.</prompt>
                      <transfer dest="+4202"/>
                    </block>
                  </form>
                  <form id="baggage">
                    <block>
                      <prompt>Please hold while we connect you to baggage services.</prompt>
                      <transfer dest="+4203"/>
                    </block>
                  </form>
                  <form id="frequent_flyer">
                    <block>
                      <prompt>Please hold while we connect you to frequent flyer member services.</prompt>
                      <transfer dest="+4204"/>
                    </block>
                  </form>
                  <form id="representative">
                    <block>
                      <prompt>Please hold while we connect you to a travel representative.</prompt>
                      <transfer dest="+4205"/>
                    </block>
                  </form>
                </vxml>
                """.formatted(name);
    }

    private String generateGenericDomainAdaptiveVxml(String description) {
        String name = escape(description != null ? description : "Customer Service IVR");
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <vxml version="2.1" xmlns="http://www.w3.org/2001/vxml">
                  <form id="start">
                    <block>
                      <prompt>Welcome to %s. Press 1 for general inquiries. Press 2 for customer support. Press 3 for office hours and location. Press 0 to speak to an operator.</prompt>
                      <goto next="#menu"/>
                    </block>
                  </form>
                  <form id="menu">
                    <menu>
                      <prompt>Please select how we can assist you today.</prompt>
                      <choice accept="digits 1" next="#inquiries"/>
                      <choice accept="digits 2" next="#support"/>
                      <choice accept="digits 3" next="#hours_location"/>
                      <choice accept="digits 0" next="#operator"/>
                    </menu>
                  </form>
                  <form id="inquiries">
                    <block>
                      <prompt>Please hold while we connect you to general inquiries.</prompt>
                      <transfer dest="+1101"/>
                    </block>
                  </form>
                  <form id="support">
                    <block>
                      <prompt>Please hold while we connect you to customer support.</prompt>
                      <transfer dest="+1102"/>
                    </block>
                  </form>
                  <form id="hours_location">
                    <block>
                      <prompt>Our business hours are Monday through Friday 9am to 5pm. Thank you for calling.</prompt>
                      <disconnect/>
                    </block>
                  </form>
                  <form id="operator">
                    <block>
                      <prompt>Please hold while we connect you to an operator.</prompt>
                      <transfer dest="+1103"/>
                    </block>
                  </form>
                </vxml>
                """.formatted(name);
    }

    public static String escape(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String cleaned = sanitizeDescription(value);
        return cleaned.replace("&", "&amp;")
                      .replace("<", "&lt;")
                      .replace(">", "&gt;")
                      .replace("\"", "&quot;")
                      .replace("'", "&apos;");
    }

    public static String sanitizeDescription(String input) {
        if (input == null || input.isBlank()) {
            return "IVR Service";
        }
        String result = input.trim();

        // 1. Un-nest JSON string if input is a JSON spec object
        if (result.startsWith("{") && result.endsWith("}")) {
            try {
                com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(result).getAsJsonObject();
                if (obj.has("business_domain") && !obj.get("business_domain").getAsString().isBlank()) {
                    result = obj.get("business_domain").getAsString() + " IVR Service";
                } else if (obj.has("refined_prompt") && !obj.get("refined_prompt").getAsString().isBlank()) {
                    result = obj.get("refined_prompt").getAsString();
                }
            } catch (Exception ignored) {}
        }

        // 2. Strip conversation history headers
        if (result.contains("Current user request:")) {
            result = result.substring(result.lastIndexOf("Current user request:") + "Current user request:".length()).trim();
        }
        if (result.contains("Recent conversation context:")) {
            int idx = result.indexOf("Recent conversation context:");
            if (idx == 0) {
                result = result.replaceAll("(?s)Recent conversation context:.*?(Current user request:|$)", "").trim();
            }
        }

        // 3. Strip XML/VXML tags
        if (result.contains("<?xml") || result.contains("<vxml") || result.contains("<form") || result.contains("<prompt") || result.contains("<")) {
            result = result.replaceAll("(?s)<\\?xml.*?>", "")
                           .replaceAll("(?s)<.*?>", " ")
                           .replaceAll("\\s+", " ")
                           .trim();
        }

        // 4. Remove duplicate nested "Welcome to Welcome to"
        while (result.toLowerCase().contains("welcome to welcome to")) {
            result = result.replaceAll("(?i)welcome to\\s+welcome to", "Welcome to").trim();
        }

        // 5. Remove any JSON key name leakage
        result = result.replaceAll("(?i)\"(refined_prompt|business_domain|departments|menu_options|greeting|closing)\":", "").trim();

        // 6. Clean newlines and whitespace
        result = result.replaceAll("[\\r\\n\\t]+", " ").replaceAll("\\s+", " ").trim();

        // 7. If result is too long or contains complex prompt text, cap it cleanly
        if (result.length() > 50) {
            int dotIdx = result.indexOf('.');
            if (dotIdx > 0 && dotIdx < 50) {
                result = result.substring(0, dotIdx).trim();
            } else {
                result = result.substring(0, 47).trim() + "...";
            }
        }

        return result.isBlank() ? "IVR Service" : result;
    }
}
