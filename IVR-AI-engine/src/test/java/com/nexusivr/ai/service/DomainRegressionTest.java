package com.nexusivr.ai.service;

import com.nexusivr.ai.model.flow.FlowModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DomainRegressionTest {

    private static final String[] DOMAINS = {
            "telecom", "banking", "hospital", "airline", "university", "restaurant"
    };

    @Test
    void testAllDomainsGenerateParseableVoiceXml() {
        for (String domain : DOMAINS) {
            String prompt = "Create a " + domain + " IVR flow";
            String systemInstruction = com.nexusivr.ai.ai.PromptBuilder.FLOW_GENERATOR_SYSTEM_INSTRUCTION;

            String llmOutput = simulateLlmOutput(domain);

            String normalized;
            try {
                normalized = LlmResponseNormalizer.normalize(llmOutput);
            } catch (LlmResponseNormalizationException e) {
                fail("Domain '" + domain + "' normalization failed: " + e.getMessage());
                return;
            }

            assertTrue(normalized.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"),
                    "Domain '" + domain + "' must start with XML declaration");
            assertTrue(normalized.contains("<vxml version=\"2.1\""),
                    "Domain '" + domain + "' must contain <vxml version=\"2.1\">");
            assertTrue(normalized.contains("</vxml>"),
                    "Domain '" + domain + "' must contain closing </vxml>");
            assertFalse(normalized.contains("```"),
                    "Domain '" + domain + "' must not contain markdown fences");
            assertFalse(normalized.contains("{"),
                    "Domain '" + domain + "' must not contain JSON braces");

            FlowModel model;
            try {
                model = new VxmlToModelConverter().convert(normalized);
            } catch (Exception e) {
                fail("Domain '" + domain + "' parsing failed: " + e.getMessage());
                return;
            }

            assertNotNull(model, "Domain '" + domain + "' FlowModel must not be null");
            assertTrue(model.getNodes().size() >= 2,
                    "Domain '" + domain + "' must have at least 2 nodes, got: " + model.getNodes().size());

            String exported = new ModelToVxmlExporter().export(model);
            assertNotNull(exported, "Domain '" + domain + "' export must not be null");
            assertTrue(exported.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"),
                    "Domain '" + domain + "' export must start with XML declaration");
            assertTrue(exported.contains("<vxml version=\"2.1\""),
                    "Domain '" + domain + "' export must contain <vxml>");
            assertTrue(exported.contains("</vxml>"),
                    "Domain '" + domain + "' export must contain closing </vxml>");

            assertFalse(exported.contains("Welcome to our service."),
                    "Domain '" + domain + "' must not contain generic placeholder prompt");

            for (char c : exported.toCharArray()) {
                if (c == '&') {
                    fail("Domain '" + domain + "' export contains bare '&' - all ampersands must be escaped");
                }
            }
        }
    }

    private String simulateLlmOutput(String domain) {
        return switch (domain) {
            case "telecom" -> """
                <?xml version="1.0" encoding="UTF-8"?>
                <vxml version="2.1" xmlns="http://www.w3.org/2001/vxml">
                  <form id="start">
                    <block>
                      <prompt>Welcome to Telecom Support. Press 1 for outages, Press 2 for billing.</prompt>
                      <goto next="#menu"/>
                    </block>
                  </form>
                  <form id="menu">
                    <menu>
                      <prompt>Please select an option.</prompt>
                      <choice accept="digits 1" next="#outage"/>
                      <choice accept="digits 2" next="#billing"/>
                    </menu>
                  </form>
                  <form id="outage">
                    <block>
                      <prompt>Please hold while we check network status.</prompt>
                      <transfer dest="+2001"/>
                    </block>
                  </form>
                  <form id="billing">
                    <block>
                      <prompt>Please hold while we connect you to billing.</prompt>
                      <disconnect/>
                    </block>
                  </form>
                </vxml>
                """;
            case "banking" -> """
                <?xml version="1.0" encoding="UTF-8"?>
                <vxml version="2.1" xmlns="http://www.w3.org/2001/vxml">
                  <form id="start">
                    <block>
                      <prompt>Welcome to Secure Bank. For your security, please enter your 4 digit PIN followed by pound.</prompt>
                      <goto next="#authenticate"/>
                    </block>
                  </form>
                  <form id="authenticate">
                    <field name="pin">
                      <prompt>Enter your 4 digit PIN followed by pound.</prompt>
                      <grammar mode="dtmf" version="1.0">
                        <rule id="digits">
                          <one-of>
                            <item>0</item><item>1</item><item>2</item><item>3</item><item>4</item>
                            <item>5</item><item>6</item><item>7</item><item>8</item><item>9</item>
                          </one-of>
                        </rule>
                      </grammar>
                      <filled>
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
                      <prompt>Press 1 for balance. Press 2 for cards. Press 3 for loans. Press 0 to speak to an agent.</prompt>
                      <choice accept="digits 1" next="#balance"/>
                      <choice accept="digits 2" next="#cards"/>
                      <choice accept="digits 3" next="#loans"/>
                      <choice accept="digits 0" next="#agent"/>
                    </menu>
                  </form>
                  <form id="balance">
                    <block>
                      <prompt>Your available balance is 1,234 dollars and 56 cents.</prompt>
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
                </vxml>
                """;
            case "hospital" -> """
                <?xml version="1.0" encoding="UTF-8"?>
                <vxml version="2.1" xmlns="http://www.w3.org/2001/vxml">
                  <form id="start">
                    <block>
                      <prompt>Welcome to City Hospital. Press 1 for appointments, Press 2 for lab results, Press 3 for emergency.</prompt>
                      <goto next="#menu"/>
                    </block>
                  </form>
                  <form id="menu">
                    <menu>
                      <prompt>Please select an option.</prompt>
                      <choice accept="digits 1" next="#appointments"/>
                      <choice accept="digits 2" next="#lab"/>
                      <choice accept="digits 3" next="#emergency"/>
                    </menu>
                  </form>
                  <form id="appointments">
                    <block>
                      <prompt>Please hold while we connect you to appointments.</prompt>
                      <transfer dest="+4001"/>
                    </block>
                  </form>
                  <form id="lab">
                    <block>
                      <prompt>Please hold while we connect you to lab results.</prompt>
                      <transfer dest="+4002"/>
                    </block>
                  </form>
                  <form id="emergency">
                    <block>
                      <prompt>Connecting you to emergency services.</prompt>
                      <transfer dest="+4003"/>
                    </block>
                  </form>
                </vxml>
                """;
            case "airline" -> """
                <?xml version="1.0" encoding="UTF-8"?>
                <vxml version="2.1" xmlns="http://www.w3.org/2001/vxml">
                  <form id="start">
                    <block>
                      <prompt>Welcome to Sky Airlines. Press 1 for flight status, Press 2 for booking, Press 3 for baggage.</prompt>
                      <goto next="#menu"/>
                    </block>
                  </form>
                  <form id="menu">
                    <menu>
                      <prompt>Please select an option.</prompt>
                      <choice accept="digits 1" next="#status"/>
                      <choice accept="digits 2" next="#booking"/>
                      <choice accept="digits 3" next="#baggage"/>
                    </menu>
                  </form>
                  <form id="status">
                    <block>
                      <prompt>Please hold while we check flight status.</prompt>
                      <transfer dest="+5001"/>
                    </block>
                  </form>
                  <form id="booking">
                    <block>
                      <prompt>Please hold while we connect you to reservations.</prompt>
                      <transfer dest="+5002"/>
                    </block>
                  </form>
                  <form id="baggage">
                    <block>
                      <prompt>Please hold while we connect you to baggage services.</prompt>
                      <disconnect/>
                    </block>
                  </form>
                </vxml>
                """;
            case "university" -> """
                <?xml version="1.0" encoding="UTF-8"?>
                <vxml version="2.1" xmlns="http://www.w3.org/2001/vxml">
                  <form id="start">
                    <block>
                      <prompt>Welcome to State University. Press 1 for admissions, Press 2 for financial aid, Press 3 for registrar.</prompt>
                      <goto next="#menu"/>
                    </block>
                  </form>
                  <form id="menu">
                    <menu>
                      <prompt>Please select an option.</prompt>
                      <choice accept="digits 1" next="#admissions"/>
                      <choice accept="digits 2" next="#finaid"/>
                      <choice accept="digits 3" next="#registrar"/>
                    </menu>
                  </form>
                  <form id="admissions">
                    <block>
                      <prompt>Please hold while we connect you to admissions.</prompt>
                      <transfer dest="+6001"/>
                    </block>
                  </form>
                  <form id="finaid">
                    <block>
                      <prompt>Please hold while we connect you to financial aid.</prompt>
                      <transfer dest="+6002"/>
                    </block>
                  </form>
                  <form id="registrar">
                    <block>
                      <prompt>Please hold while we connect you to the registrar.</prompt>
                      <disconnect/>
                    </block>
                  </form>
                </vxml>
                """;
            case "restaurant" -> """
                <?xml version="1.0" encoding="UTF-8"?>
                <vxml version="2.1" xmlns="http://www.w3.org/2001/vxml">
                  <form id="start">
                    <block>
                      <prompt>Welcome to Pizza Bistro. Press 1 for takeout orders, Press 2 for reservations, Press 3 for hours and location, Press 0 to speak to our hostess.</prompt>
                      <goto next="#menu"/>
                    </block>
                  </form>
                  <form id="menu">
                    <menu>
                      <prompt>Please select an option.</prompt>
                      <choice accept="digits 1" next="#orders"/>
                      <choice accept="digits 2" next="#reservations"/>
                      <choice accept="digits 3" next="#info"/>
                      <choice accept="digits 0" next="#hostess"/>
                    </menu>
                  </form>
                  <form id="orders">
                    <block>
                      <prompt>Please hold while we connect you to our takeout order line.</prompt>
                      <transfer dest="+7001"/>
                    </block>
                  </form>
                  <form id="reservations">
                    <block>
                      <prompt>Please hold while we connect you to our reservation desk.</prompt>
                      <transfer dest="+7002"/>
                    </block>
                  </form>
                  <form id="info">
                    <block>
                      <prompt>We are located at 123 Main Street. Our hours are Monday through Friday 11am to 10pm. Thank you for calling.</prompt>
                      <disconnect/>
                    </block>
                  </form>
                  <form id="hostess">
                    <block>
                      <prompt>Please hold while we connect you to our hostess station.</prompt>
                      <transfer dest="+7003"/>
                    </block>
                  </form>
                </vxml>
                """;
            default -> throw new IllegalArgumentException("Unknown domain: " + domain);
        };
    }
}
