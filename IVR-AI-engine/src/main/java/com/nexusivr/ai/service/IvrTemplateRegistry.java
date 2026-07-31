package com.nexusivr.ai.service;

import com.nexusivr.ai.model.IvrTemplate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry providing reusable, domain-specific IVR templates to ensure routing consistency.
 */
public class IvrTemplateRegistry {

    private static final Logger logger = LoggerFactory.getLogger(IvrTemplateRegistry.class);
    private static final Map<String, IvrTemplate> TEMPLATES = new LinkedHashMap<>();

    static {
        // 1. Banking Template
        TEMPLATES.put("banking", new IvrTemplate(
                "Banking",
                "Secure banking IVR with user authentication and account services.",
                List.of("start", "greeting", "dtmf_input", "hours", "dtmf_menu", "queue", "transfer", "end"),
                "Greeting -> Card/Account Input (Authentication) -> Hours Check -> Balance & Services Menu -> Queue -> Agent Transfer",
                "Press 1 for Account Balance, Press 2 for Credit Cards, Press 3 for Loan Status, Press 4 to speak to an agent.",
                List.of("Banking Support Queue", "Card Services Queue"),
                List.of("Transfer to Fraud hotline", "Transfer to Agent"),
                "Welcome to Global Bank. For security, please enter your 16-digit card or account number followed by pound.",
                "Check business hours before transferring to live support queues.",
                "Hangs up after 3 incorrect authentication attempts.",
                "Re-prompts after 5 seconds of silence, max 2 timeouts.",
                "{\"name\":\"Banking Template\",\"description\":\"Standard secure banking IVR flow\",\"nodes\":[{\"id\":\"n1\",\"type\":\"start\",\"title\":\"Start\",\"subtitle\":\"Entry Point\"},{\"id\":\"n2\",\"type\":\"greeting\",\"title\":\"Welcome Greeting\",\"subtitle\":\"Welcome to Global Bank\"},{\"id\":\"n3\",\"type\":\"dtmf_input\",\"title\":\"Verify Account Number \title\":\"Card Authentication\" PIN\",\"subtitle\":\"Collect card number\"},{\"id\":\"n4\",\"type\":\"hours\",\"title\":\"Business Hours Check\",\"subtitle\":\"Check open hours\"},{\"id\":\"n5\",\"type\":\"dtmf_menu\",\"title\":\"Account Services Menu\",\"subtitle\":\"Balance, Cards, Loans, Agent\"},{\"id\":\"n6\",\"type\":\"queue\",\"title\":\"Banking Support Queue\",\"subtitle\":\"Queue callers for agents\"},{\"id\":\"n7\",\"type\":\"transfer\",\"title\":\"Transfer to Banking Representative\",\"subtitle\":\"Transfer call\"},{\"id\":\"n8\",\"type\":\"end\",\"title\":\"End Call\",\"subtitle\":\"Hang up\"}],\"edges\":[{\"id\":\"e1\",\"sourceId\":\"n1\",\"sourcePort\":\"out\",\"targetId\":\"n2\",\"targetPort\":\"in\"},{\"id\":\"e2\",\"sourceId\":\"n2\",\"sourcePort\":\"out\",\"targetId\":\"n3\",\"targetPort\":\"in\"},{\"id\":\"e3\",\"sourceId\":\"n3\",\"sourcePort\":\"success\",\"targetId\":\"n4\",\"targetPort\":\"in\"},{\"id\":\"e4\",\"sourceId\":\"n3\",\"sourcePort\":\"timeout\",\"targetId\":\"n8\",\"targetPort\":\"in\"},{\"id\":\"e5\",\"sourceId\":\"n4\",\"sourcePort\":\"open\",\"targetId\":\"n5\",\"targetPort\":\"in\"},{\"id\":\"e6\",\"sourceId\":\"n4\",\"sourcePort\":\"closed\",\"targetId\":\"n8\",\"targetPort\":\"in\"},{\"id\":\"e7\",\"sourceId\":\"n5\",\"sourcePort\":\"key1\",\"targetId\":\"n6\",\"targetPort\":\"in\"},{\"id\":\"e8\",\"sourceId\":\"n5\",\"sourcePort\":\"key2\",\"targetId\":\"n8\",\"targetPort\":\"in\"},{\"id\":\"e9\",\"sourceId\":\"n5\",\"sourcePort\":\"key3\",\"targetId\":\"n8\",\"targetPort\":\"in\"},{\"id\":\"e10\",\"sourceId\":\"n5\",\"sourcePort\":\"key4\",\"targetId\":\"n6\",\"targetPort\":\"in\"},{\"id\":\"e11\",\"sourceId\":\"n5\",\"sourcePort\":\"timeout\",\"targetId\":\"n8\",\"targetPort\":\"in\"},{\"id\":\"e12\",\"sourceId\":\"n6\",\"sourcePort\":\"answered\",\"targetId\":\"n7\",\"targetPort\":\"in\"},{\"id\":\"e13\",\"sourceId\":\"n6\",\"sourcePort\":\"overflow\",\"targetId\":\"n8\",\"targetPort\":\"in\"},{\"id\":\"e14\",\"sourceId\":\"n7\",\"sourcePort\":\"success\",\"targetId\":\"n8\",\"targetPort\":\"in\"},{\"id\":\"e15\",\"sourceId\":\"n7\",\"sourcePort\":\"fail\",\"targetId\":\"n8\",\"targetPort\":\"in\"}]}"
        ));

        // 2. Healthcare Template
        TEMPLATES.put("healthcare", new IvrTemplate(
                "Healthcare",
                "Clinical IVR containing appointment scheduling and triage routing.",
                List.of("start", "greeting", "hours", "dtmf_menu", "queue", "transfer", "end"),
                "Greeting -> Hours Check -> Patient Menu -> Medical Triage/Scheduling Queue -> Nurse Line Transfer",
                "Press 1 for Appointments, Press 2 for Pharmacy and Refills, Press 3 for Billing, Press 4 for Emergency Triage.",
                List.of("Clinical Appointments Queue", "Billing Queue"),
                List.of("Transfer to Emergency Nurse line", "Transfer to Receptionist"),
                "Thank you for calling City Healthcare. If you are experiencing a life-threatening medical emergency, please hang up and dial 911 immediately.",
                "Check hours immediately after greeting; closed hours route to the on-call doctor.",
                "Invalid inputs route to the main reception desk.",
                "Re-prompts the options twice, then routes to receptionist.",
                "{\"name\":\"Healthcare Template\",\"description\":\"Standard healthcare triage flow\",\"nodes\":[{\"id\":\"n1\",\"type\":\"start\",\"title\":\"Start\",\"subtitle\":\"Entry Point\"},{\"id\":\"n2\",\"type\":\"greeting\",\"title\":\"Welcome & Emergency Warning\",\"subtitle\":\"Triage warning message\"},{\"id\":\"n3\",\"type\":\"hours\",\"title\":\"Business Hours Check\",\"subtitle\":\"Route after-hours to doctor\"},{\"id\":\"n4\",\"type\":\"dtmf_menu\",\"title\":\"Patient Triage Menu\",\"subtitle\":\"Select department\"},{\"id\":\"n5\",\"type\":\"queue\",\"title\":\"Appointments Queue\",\"subtitle\":\"Queue for booking staff\"},{\"id\":\"n6\",\"type\":\"transfer\",\"title\":\"Nurse Line Transfer\",\"subtitle\":\"Transfer to nurse\"},{\"id\":\"n7\",\"type\":\"end\",\"title\":\"End Call\",\"subtitle\":\"Hang up\"}],\"edges\":[{\"id\":\"e1\",\"sourceId\":\"n1\",\"sourcePort\":\"out\",\"targetId\":\"n2\",\"targetPort\":\"in\"},{\"id\":\"e2\",\"sourceId\":\"n2\",\"sourcePort\":\"out\",\"targetId\":\"n3\",\"targetPort\":\"in\"},{\"id\":\"e3\",\"sourceId\":\"n3\",\"sourcePort\":\"open\",\"targetId\":\"n4\",\"targetPort\":\"in\"},{\"id\":\"e4\",\"sourceId\":\"n3\",\"sourcePort\":\"closed\",\"targetId\":\"n7\",\"targetPort\":\"in\"},{\"id\":\"e5\",\"sourceId\":\"n4\",\"sourcePort\":\"key1\",\"targetId\":\"n5\",\"targetPort\":\"in\"},{\"id\":\"e6\",\"sourceId\":\"n4\",\"sourcePort\":\"key2\",\"targetId\":\"n6\",\"targetPort\":\"in\"},{\"id\":\"e7\",\"sourceId\":\"n4\",\"sourcePort\":\"key3\",\"targetId\":\"n7\",\"targetPort\":\"in\"},{\"id\":\"e8\",\"sourceId\":\"n4\",\"sourcePort\":\"timeout\",\"targetId\":\"n7\",\"targetPort\":\"in\"},{\"id\":\"e9\",\"sourceId\":\"n5\",\"sourcePort\":\"answered\",\"targetId\":\"n7\",\"targetPort\":\"in\"},{\"id\":\"e10\",\"sourceId\":\"n5\",\"sourcePort\":\"overflow\",\"targetId\":\"n7\",\"targetPort\":\"in\"},{\"id\":\"e11\",\"sourceId\":\"n6\",\"sourcePort\":\"success\",\"targetId\":\"n7\",\"targetPort\":\"in\"},{\"id\":\"e12\",\"sourceId\":\"n6\",\"sourcePort\":\"fail\",\"targetId\":\"n7\",\"targetPort\":\"in\"}]}"
        ));

        // 3. Hospitality Template
        TEMPLATES.put("hospitality", new IvrTemplate(
                "Hospitality",
                "Hotel front desk concierge, booking, and guest services routing.",
                List.of("start", "greeting", "dtmf_menu", "queue", "transfer", "end"),
                "Greeting -> Guest Menu -> Booking/Room Service Queues -> Room Service/Front Desk Transfer",
                "Press 1 for Room Reservations, Press 2 for Front Desk, Press 3 for Room Service, Press 4 for Housekeeping.",
                List.of("Reservations Queue", "Room Service Queue"),
                List.of("Transfer to Front Desk Operator", "Transfer to Housekeeping lead"),
                "Welcome to Grand Resort Concierge. How can we make your stay exceptional today?",
                "Ensure front desk options are available 24/7.",
                "Default option routes to front desk operator.",
                "Re-prompts options on silence.",
                "{\"name\":\"Hospitality Template\",\"description\":\"Hotel concierge flow\",\"nodes\":[{\"id\":\"n1\",\"type\":\"start\",\"title\":\"Start\",\"subtitle\":\"Entry Point\"},{\"id\":\"n2\",\"type\":\"greeting\",\"title\":\"Concierge Welcome\",\"subtitle\":\"Welcome to Grand Resort\"},{\"id\":\"n3\",\"type\":\"dtmf_menu\",\"title\":\"Concierge Menu\",\"subtitle\":\"Reservations, Front Desk, Room Service\"},{\"id\":\"n4\",\"type\":\"transfer\",\"title\":\"Front Desk Transfer\",\"subtitle\":\"Transfer to Front Desk\"},{\"id\":\"n5\",\"type\":\"end\",\"title\":\"End Call\",\"subtitle\":\"Hang up\"}],\"edges\":[{\"id\":\"e1\",\"sourceId\":\"n1\",\"sourcePort\":\"out\",\"targetId\":\"n2\",\"targetPort\":\"in\"},{\"id\":\"e2\",\"sourceId\":\"n2\",\"sourcePort\":\"out\",\"targetId\":\"n3\",\"targetPort\":\"in\"},{\"id\":\"e3\",\"sourceId\":\"n3\",\"sourcePort\":\"key1\",\"targetId\":\"n4\",\"targetPort\":\"in\"},{\"id\":\"e4\",\"sourceId\":\"n3\",\"sourcePort\":\"key2\",\"targetId\":\"n5\",\"targetPort\":\"in\"},{\"id\":\"e5\",\"sourceId\":\"n3\",\"sourcePort\":\"key3\",\"targetId\":\"n5\",\"targetPort\":\"in\"},{\"id\":\"e6\",\"sourceId\":\"n3\",\"sourcePort\":\"timeout\",\"targetId\":\"n5\",\"targetPort\":\"in\"},{\"id\":\"e7\",\"sourceId\":\"n4\",\"sourcePort\":\"success\",\"targetId\":\"n5\",\"targetPort\":\"in\"},{\"id\":\"e8\",\"sourceId\":\"n4\",\"sourcePort\":\"fail\",\"targetId\":\"n5\",\"targetPort\":\"in\"}]}"
        ));

        // 4. Telecom Template
        TEMPLATES.put("telecom", new IvrTemplate(
                "Telecom",
                "Telecom billing support, mobile plan services, and SIM activation.",
                List.of("start", "greeting", "dtmf_menu", "queue", "transfer", "end"),
                "Greeting -> Account Menu -> Billing & SIM Queues -> Billing Specialist Transfer",
                "Press 1 for Account & Billing, Press 2 for Roaming services, Press 3 for SIM swap and activation.",
                List.of("Billing Queue", "SIM Swap Support Queue"),
                List.of("Transfer to Billing Specialist", "Transfer to SIM swap agent"),
                "Thank you for calling Apex Telecom. Let's get your account questions answered.",
                "Standard routing checks account status beforehand.",
                "Sends to automated website link via TTS on incorrect digits.",
                "Repeat prompt and disconnect after 3 minutes.",
                "{\"name\":\"Telecom Template\",\"description\":\"Telecom billing and SIM flow\",\"nodes\":[{\"id\":\"n1\",\"type\":\"start\",\"title\":\"Start\",\"subtitle\":\"Entry Point\"},{\"id\":\"n2\",\"type\":\"greeting\",\"title\":\"Telecom Greeting\",\"subtitle\":\"Welcome to Apex Telecom\"},{\"id\":\"n3\",\"type\":\"dtmf_menu\",\"title\":\"Telecom Menu\",\"subtitle\":\"Billing, Roaming, SIM Swap\"},{\"id\":\"n4\",\"type\":\"queue\",\"title\":\"Billing Queue\",\"subtitle\":\"Queue for billing\"},{\"id\":\"n5\",\"type\":\"end\",\"title\":\"End Call\",\"subtitle\":\"Hang up\"}],\"edges\":[{\"id\":\"e1\",\"sourceId\":\"n1\",\"sourcePort\":\"out\",\"targetId\":\"n2\",\"targetPort\":\"in\"},{\"id\":\"e2\",\"sourceId\":\"n2\",\"sourcePort\":\"out\",\"targetId\":\"n3\",\"targetPort\":\"in\"},{\"id\":\"e3\",\"sourceId\":\"n3\",\"sourcePort\":\"key1\",\"targetId\":\"n4\",\"targetPort\":\"in\"},{\"id\":\"e4\",\"sourceId\":\"n3\",\"sourcePort\":\"key2\",\"targetId\":\"n5\",\"targetPort\":\"in\"},{\"id\":\"e5\",\"sourceId\":\"n3\",\"sourcePort\":\"key3\",\"targetId\":\"n5\",\"targetPort\":\"in\"},{\"id\":\"e6\",\"sourceId\":\"n3\",\"sourcePort\":\"timeout\",\"targetId\":\"n5\",\"targetPort\":\"in\"},{\"id\":\"e7\",\"sourceId\":\"n4\",\"sourcePort\":\"answered\",\"targetId\":\"n5\",\"targetPort\":\"in\"},{\"id\":\"e8\",\"sourceId\":\"n4\",\"sourcePort\":\"overflow\",\"targetId\":\"n5\",\"targetPort\":\"in\"}]}"
        ));

        // 5. Government Template
        TEMPLATES.put("government", new IvrTemplate(
                "Government",
                "Citizen inquiry helpline for departments, permits, and tax info.",
                List.of("start", "greeting", "hours", "dtmf_menu", "queue", "transfer", "end"),
                "Greeting -> Hours Check -> Info Menu -> Tax/Permits Desk Queue -> Citizen Support Officer Transfer",
                "Press 1 for Business Permits, Press 2 for Tax Information, Press 3 for Office Locations and Hours.",
                List.of("Citizen Permits Queue", "Tax Info Queue"),
                List.of("Transfer to Permit Specialist", "Transfer to Information Desk"),
                "Thank you for contacting the Citizen Service hotline. Please choose from the following municipal services.",
                "Hours check must occur before any transfers to support officers.",
                "TTS playback explaining public business hours and location.",
                "Direct transfer to main county clerk on invalid selection.",
                "{\"name\":\"Government Template\",\"description\":\"Citizen services IVR\",\"nodes\":[{\"id\":\"n1\",\"type\":\"start\",\"title\":\"Start\",\"subtitle\":\"Entry Point\"},{\"id\":\"n2\",\"type\":\"greeting\",\"title\":\"Welcome Greeting\",\"subtitle\":\"Citizen service hotline\"},{\"id\":\"n3\",\"type\":\"hours\",\"title\":\"Public Office Hours\",\"subtitle\":\"Hours check\"},{\"id\":\"n4\",\"type\":\"dtmf_menu\",\"title\":\"Citizen Menu\",\"subtitle\":\"Permits, Taxes, Locations\"},{\"id\":\"n5\",\"type\":\"end\",\"title\":\"End Call\",\"subtitle\":\"Hang up\"}],\"edges\":[{\"id\":\"e1\",\"sourceId\":\"n1\",\"sourcePort\":\"out\",\"targetId\":\"n2\",\"targetPort\":\"in\"},{\"id\":\"e2\",\"sourceId\":\"n2\",\"sourcePort\":\"out\",\"targetId\":\"n3\",\"targetPort\":\"in\"},{\"id\":\"e3\",\"sourceId\":\"n3\",\"sourcePort\":\"open\",\"targetId\":\"n4\",\"targetPort\":\"in\"},{\"id\":\"e4\",\"sourceId\":\"n3\",\"sourcePort\":\"closed\",\"targetId\":\"n5\",\"targetPort\":\"in\"},{\"id\":\"e5\",\"sourceId\":\"n4\",\"sourcePort\":\"key1\",\"targetId\":\"n5\",\"targetPort\":\"in\"},{\"id\":\"e6\",\"sourceId\":\"n4\",\"sourcePort\":\"key2\",\"targetId\":\"n5\",\"targetPort\":\"in\"},{\"id\":\"e7\",\"sourceId\":\"n4\",\"sourcePort\":\"key3\",\"targetId\":\"n5\",\"targetPort\":\"in\"},{\"id\":\"e8\",\"sourceId\":\"n4\",\"sourcePort\":\"timeout\",\"targetId\":\"n5\",\"targetPort\":\"in\"}]}"
        ));

        // 6. Restaurant Template
        TEMPLATES.put("restaurant", new IvrTemplate(
                "Restaurant",
                "Food order placement, reservations, and location info.",
                List.of("start", "greeting", "dtmf_menu", "queue", "transfer", "end"),
                "Greeting -> Food & Booking Menu -> Orders Queue -> Hostess Transfer",
                "Press 1 to Place an Order, Press 2 for Reservations, Press 3 for Hours and Location.",
                List.of("Takeout Orders Queue"),
                List.of("Transfer to Hostess Station", "Transfer to Kitchen manager"),
                "Thank you for calling Pizza Bistro. Ready to taste the best slice in town?",
                "Menu option 3 plays address and opening hours details directly.",
                "Routes to orders queue immediately on error.",
                "Repeat menu up to 3 times, then route call to cashier.",
                "{\"name\":\"Restaurant Template\",\"description\":\"Restaurant ordering IVR\",\"nodes\":[{\"id\":\"n1\",\"type\":\"start\",\"title\":\"Start\",\"subtitle\":\"Entry Point\"},{\"id\":\"n2\",\"type\":\"greeting\",\"title\":\"Pizzeria Welcome\",\"subtitle\":\"Welcome to Pizza Bistro\"},{\"id\":\"n3\",\"type\":\"dtmf_menu\",\"title\":\"Ordering Menu\",\"subtitle\":\"Takeout, Reservation, Info\"},{\"id\":\"n4\",\"type\":\"end\",\"title\":\"End Call\",\"subtitle\":\"Hang up\"}],\"edges\":[{\"id\":\"e1\",\"sourceId\":\"n1\",\"sourcePort\":\"out\",\"targetId\":\"n2\",\"targetPort\":\"in\"},{\"id\":\"e2\",\"sourceId\":\"n2\",\"sourcePort\":\"out\",\"targetId\":\"n3\",\"targetPort\":\"in\"},{\"id\":\"e3\",\"sourceId\":\"n3\",\"sourcePort\":\"key1\",\"targetId\":\"n4\",\"targetPort\":\"in\"},{\"id\":\"e4\",\"sourceId\":\"n3\",\"sourcePort\":\"key2\",\"targetId\":\"n4\",\"targetPort\":\"in\"},{\"id\":\"e5\",\"sourceId\":\"n3\",\"sourcePort\":\"key3\",\"targetId\":\"n4\",\"targetPort\":\"in\"},{\"id\":\"e6\",\"sourceId\":\"n3\",\"sourcePort\":\"timeout\",\"targetId\":\"n4\",\"targetPort\":\"in\"}]}"
        ));

        // 7. Airline Template
        TEMPLATES.put("airline", new IvrTemplate(
                "Airline",
                "Flight schedules, booking support, and baggage claim updates.",
                List.of("start", "greeting", "dtmf_input", "dtmf_menu", "queue", "transfer", "end"),
                "Greeting -> Confirmation Code Input -> Air Travel Menu -> Booking Queues -> Agent Transfer",
                "Press 1 for Flight Status, Press 2 for New Bookings, Press 3 for Baggage claim, Press 4 to speak to an agent.",
                List.of("Airlines Baggage Queue", "Reservations Queue"),
                List.of("Transfer to Booking Agent", "Transfer to Baggage desk"),
                "Welcome to Horizon Air. Please enter your 6-character flight confirmation code or press pound to continue.",
                "Check booking lookup system prior to queuing agent.",
                "Repeat flight status information option.",
                "Repeat menu prompts, route to baggage queue on input timeout.",
                "{\"name\":\"Airline Template\",\"description\":\"Airline booking IVR\",\"nodes\":[{\"id\":\"n1\",\"type\":\"start\",\"title\":\"Start\",\"subtitle\":\"Entry Point\"},{\"id\":\"n2\",\"type\":\"greeting\",\"title\":\"Horizon Greeting\",\"subtitle\":\"Welcome to Horizon Air\"},{\"id\":\"n3\",\"type\":\"dtmf_menu\",\"title\":\"Airlines Menu\",\"subtitle\":\"Status, Booking, Baggage, Agent\"},{\"id\":\"n4\",\"type\":\"end\",\"title\":\"End Call\",\"subtitle\":\"Hang up\"}],\"edges\":[{\"id\":\"e1\",\"sourceId\":\"n1\",\"sourcePort\":\"out\",\"targetId\":\"n2\",\"targetPort\":\"in\"},{\"id\":\"e2\",\"sourceId\":\"n2\",\"sourcePort\":\"out\",\"targetId\":\"n3\",\"targetPort\":\"in\"},{\"id\":\"e3\",\"sourceId\":\"n3\",\"sourcePort\":\"key1\",\"targetId\":\"n4\",\"targetPort\":\"in\"},{\"id\":\"e4\",\"sourceId\":\"n3\",\"sourcePort\":\"key2\",\"targetId\":\"n4\",\"targetPort\":\"in\"},{\"id\":\"e5\",\"sourceId\":\"n3\",\"sourcePort\":\"key3\",\"targetId\":\"n4\",\"targetPort\":\"in\"},{\"id\":\"e6\",\"sourceId\":\"n3\",\"sourcePort\":\"key4\",\"targetId\":\"n4\",\"targetPort\":\"in\"},{\"id\":\"e7\",\"sourceId\":\"n3\",\"sourcePort\":\"timeout\",\"targetId\":\"n4\",\"targetPort\":\"in\"}]}"
        ));

        // 8. Retail Template
        TEMPLATES.put("retail", new IvrTemplate(
                "Retail",
                "Order tracking status, refund support, and retail store location.",
                List.of("start", "greeting", "dtmf_input", "dtmf_menu", "queue", "transfer", "end"),
                "Greeting -> Order ID Input -> Store Menu -> Orders Queue -> Support Desk Transfer",
                "Press 1 for Order Status & Tracking, Press 2 for Returns & Refunds, Press 3 for Store Locations and Hours.",
                List.of("Customer Support Queue", "Returns Queue"),
                List.of("Transfer to Returns desk", "Transfer to General support"),
                "Thank you for calling SuperStore. Please enter your order number or press star.",
                "Verify order status via API node before menu routing.",
                "Transfer directly to receptionist.",
                "Re-prompt and transfer call after 15 seconds.",
                "{\"name\":\"Retail Template\",\"description\":\"Retail support IVR\",\"nodes\":[{\"id\":\"n1\",\"type\":\"start\",\"title\":\"Start\",\"subtitle\":\"Entry Point\"},{\"id\":\"n2\",\"type\":\"greeting\",\"title\":\"Retail Greeting\",\"subtitle\":\"Welcome to SuperStore\"},{\"id\":\"n3\",\"type\":\"dtmf_menu\",\"title\":\"SuperStore Menu\",\"subtitle\":\"Order, Return, Location\"},{\"id\":\"n4\",\"type\":\"end\",\"title\":\"End Call\",\"subtitle\":\"Hang up\"}],\"edges\":[{\"id\":\"e1\",\"sourceId\":\"n1\",\"sourcePort\":\"out\",\"targetId\":\"n2\",\"targetPort\":\"in\"},{\"id\":\"e2\",\"sourceId\":\"n2\",\"sourcePort\":\"out\",\"targetId\":\"n3\",\"targetPort\":\"in\"},{\"id\":\"e3\",\"sourceId\":\"n3\",\"sourcePort\":\"key1\",\"targetId\":\"n4\",\"targetPort\":\"in\"},{\"id\":\"e4\",\"sourceId\":\"n3\",\"sourcePort\":\"key2\",\"targetId\":\"n4\",\"targetPort\":\"in\"},{\"id\":\"e5\",\"sourceId\":\"n3\",\"sourcePort\":\"key3\",\"targetId\":\"n4\",\"targetPort\":\"in\"},{\"id\":\"e6\",\"sourceId\":\"n3\",\"sourcePort\":\"timeout\",\"targetId\":\"n4\",\"targetPort\":\"in\"}]}"
        ));

        // 9. Insurance Template
        TEMPLATES.put("insurance", new IvrTemplate(
                "Insurance",
                "Helpline to file claims, process payments, and verify coverage.",
                List.of("start", "greeting", "dtmf_input", "dtmf_menu", "queue", "transfer", "end"),
                "Greeting -> Policy Number Input -> Coverage Menu -> Claims Queue -> Claim Adjuster Transfer",
                "Press 1 to File a Claim, Press 2 for Premium Payments, Press 3 to check Policy details.",
                List.of("Claims Processing Queue", "Billing Support Queue"),
                List.of("Transfer to Claim Adjuster", "Transfer to Agent"),
                "Thank you for calling Shield Insurance. Please enter your 8-digit policy number to proceed.",
                "Perform policy lookup and check policy status before transferring.",
                "Hangs up after warning caller.",
                "Repeat menu 3 times, then disconnect.",
                "{\"name\":\"Insurance Template\",\"description\":\"Insurance claim IVR\",\"nodes\":[{\"id\":\"n1\",\"type\":\"start\",\"title\":\"Start\",\"subtitle\":\"Entry Point\"},{\"id\":\"n2\",\"type\":\"greeting\",\"title\":\"Welcome Greeting\",\"subtitle\":\"Welcome to Shield Insurance\"},{\"id\":\"n3\",\"type\":\"dtmf_menu\",\"title\":\"Shield Menu\",\"subtitle\":\"Claim, Payment, Policy\"},{\"id\":\"n4\",\"type\":\"end\",\"title\":\"End Call\",\"subtitle\":\"Hang up\"}],\"edges\":[{\"id\":\"e1\",\"sourceId\":\"n1\",\"sourcePort\":\"out\",\"targetId\":\"n2\",\"targetPort\":\"in\"},{\"id\":\"e2\",\"sourceId\":\"n2\",\"sourcePort\":\"out\",\"targetId\":\"n3\",\"targetPort\":\"in\"},{\"id\":\"e3\",\"sourceId\":\"n3\",\"sourcePort\":\"key1\",\"targetId\":\"n4\",\"targetPort\":\"in\"},{\"id\":\"e4\",\"sourceId\":\"n3\",\"sourcePort\":\"key2\",\"targetId\":\"n4\",\"targetPort\":\"in\"},{\"id\":\"e5\",\"sourceId\":\"n3\",\"sourcePort\":\"key3\",\"targetId\":\"n4\",\"targetPort\":\"in\"},{\"id\":\"e6\",\"sourceId\":\"n3\",\"sourcePort\":\"timeout\",\"targetId\":\"n4\",\"targetPort\":\"in\"}]}"
        ));

        // 10. Education Template (Fix 9b: new domain for university/campus/helpline requests)
        TEMPLATES.put("education", new IvrTemplate(
                "Education",
                "University helpline for admissions, financial aid, student services, and campus emergencies.",
                List.of("start", "greeting", "hours", "dtmf_menu", "queue", "transfer", "end"),
                "Greeting -> Hours Check -> Student Services Menu -> Admissions/Financial Aid/Emergency Queues -> Department Transfer",
                "Press 1 for Admissions, Press 2 for Financial Aid, Press 3 for Student Services, Press 4 for Campus Emergency, Press 0 to speak to the main desk.",
                List.of("Admissions Queue", "Financial Aid Queue", "Student Services Queue"),
                List.of("Transfer to Campus Emergency Security", "Transfer to Main Desk"),
                "Thank you for calling. If this is a campus emergency requiring immediate assistance, please press 4 now or hang up and dial 911.",
                "Check office hours; after-hours route directly to campus security for emergencies.",
                "Invalid input routes to main university desk.",
                "Re-prompt once, then route to main desk operator.",
                "{\"name\":\"Education Template\",\"description\":\"University helpline IVR\",\"nodes\":[{\"id\":\"n1\",\"type\":\"start\",\"title\":\"Start\",\"subtitle\":\"Entry Point\"},{\"id\":\"n2\",\"type\":\"greeting\",\"title\":\"University Welcome\",\"subtitle\":\"Campus helpline greeting\"},{\"id\":\"n3\",\"type\":\"hours\",\"title\":\"Office Hours Check\",\"subtitle\":\"Route after-hours to security\"},{\"id\":\"n4\",\"type\":\"dtmf_menu\",\"title\":\"Student Services Menu\",\"subtitle\":\"Admissions, Aid, Emergency\"},{\"id\":\"n5\",\"type\":\"queue\",\"title\":\"Admissions Queue\",\"subtitle\":\"Queue for admissions staff\"},{\"id\":\"n6\",\"type\":\"queue\",\"title\":\"Financial Aid Queue\",\"subtitle\":\"Queue for financial aid office\"},{\"id\":\"n7\",\"type\":\"transfer\",\"title\":\"Campus Emergency Transfer\",\"subtitle\":\"Transfer to campus security\"},{\"id\":\"n8\",\"type\":\"end\",\"title\":\"End Call\",\"subtitle\":\"Hang up\"}],\"edges\":[{\"id\":\"e1\",\"sourceId\":\"n1\",\"sourcePort\":\"out\",\"targetId\":\"n2\",\"targetPort\":\"in\"},{\"id\":\"e2\",\"sourceId\":\"n2\",\"sourcePort\":\"out\",\"targetId\":\"n3\",\"targetPort\":\"in\"},{\"id\":\"e3\",\"sourceId\":\"n3\",\"sourcePort\":\"open\",\"targetId\":\"n4\",\"targetPort\":\"in\"},{\"id\":\"e4\",\"sourceId\":\"n3\",\"sourcePort\":\"closed\",\"targetId\":\"n7\",\"targetPort\":\"in\"},{\"id\":\"e5\",\"sourceId\":\"n4\",\"sourcePort\":\"key1\",\"targetId\":\"n5\",\"targetPort\":\"in\"},{\"id\":\"e6\",\"sourceId\":\"n4\",\"sourcePort\":\"key2\",\"targetId\":\"n6\",\"targetPort\":\"in\"},{\"id\":\"e7\",\"sourceId\":\"n4\",\"sourcePort\":\"key3\",\"targetId\":\"n8\",\"targetPort\":\"in\"},{\"id\":\"e8\",\"sourceId\":\"n4\",\"sourcePort\":\"key4\",\"targetId\":\"n7\",\"targetPort\":\"in\"},{\"id\":\"e9\",\"sourceId\":\"n4\",\"sourcePort\":\"timeout\",\"targetId\":\"n8\",\"targetPort\":\"in\"},{\"id\":\"e10\",\"sourceId\":\"n5\",\"sourcePort\":\"answered\",\"targetId\":\"n8\",\"targetPort\":\"in\"},{\"id\":\"e11\",\"sourceId\":\"n5\",\"sourcePort\":\"overflow\",\"targetId\":\"n8\",\"targetPort\":\"in\"},{\"id\":\"e12\",\"sourceId\":\"n6\",\"sourcePort\":\"answered\",\"targetId\":\"n8\",\"targetPort\":\"in\"},{\"id\":\"e13\",\"sourceId\":\"n6\",\"sourcePort\":\"overflow\",\"targetId\":\"n8\",\"targetPort\":\"in\"},{\"id\":\"e14\",\"sourceId\":\"n7\",\"sourcePort\":\"success\",\"targetId\":\"n8\",\"targetPort\":\"in\"},{\"id\":\"e15\",\"sourceId\":\"n7\",\"sourcePort\":\"fail\",\"targetId\":\"n8\",\"targetPort\":\"in\"}]}"
        ));

        // 11. Generic Neutral Fallback Template (Fix 9b: replaces hospitality as the catch-all default
        //     so a domain-unknown request receives an obviously neutral placeholder flow rather than
        //     silently presenting hotel-specific content.)
        TEMPLATES.put("generic", new IvrTemplate(
                "Generic",
                "Neutral placeholder IVR flow used when no domain-specific template matches.",
                List.of("start", "greeting", "dtmf_menu", "queue", "transfer", "end"),
                "Greeting -> Main Menu -> Department Queue -> Agent Transfer",
                "Press 1 for Department A, Press 2 for Department B, Press 3 for Department C, Press 0 to speak to an agent.",
                List.of("Main Support Queue"),
                List.of("Transfer to Agent"),
                "Thank you for calling. Please listen carefully as our menu options have recently changed.",
                "Route to agent on invalid input.",
                "Route to agent on error.",
                "Re-prompt once, then route to agent.",
                "{\"name\":\"Generic IVR Flow\",\"description\":\"Neutral placeholder IVR — please customize for your business\",\"nodes\":[{\"id\":\"n1\",\"type\":\"start\",\"title\":\"Start\",\"subtitle\":\"Entry Point\"},{\"id\":\"n2\",\"type\":\"greeting\",\"title\":\"Welcome Greeting\",\"subtitle\":\"Thank you for calling\"},{\"id\":\"n3\",\"type\":\"dtmf_menu\",\"title\":\"Main Menu\",\"subtitle\":\"Department A, B, C, Agent\"},{\"id\":\"n4\",\"type\":\"queue\",\"title\":\"Banking Support Queue\",\"subtitle\":\"Queue for available agent\"},{\"id\":\"n5\",\"type\":\"transfer\",\"title\":\"Agent Transfer\",\"subtitle\":\"Transfer to live agent\"},{\"id\":\"n6\",\"type\":\"end\",\"title\":\"End Call\",\"subtitle\":\"Hang up\"}],\"edges\":[{\"id\":\"e1\",\"sourceId\":\"n1\",\"sourcePort\":\"out\",\"targetId\":\"n2\",\"targetPort\":\"in\"},{\"id\":\"e2\",\"sourceId\":\"n2\",\"sourcePort\":\"out\",\"targetId\":\"n3\",\"targetPort\":\"in\"},{\"id\":\"e3\",\"sourceId\":\"n3\",\"sourcePort\":\"key1\",\"targetId\":\"n4\",\"targetPort\":\"in\"},{\"id\":\"e4\",\"sourceId\":\"n3\",\"sourcePort\":\"key2\",\"targetId\":\"n4\",\"targetPort\":\"in\"},{\"id\":\"e5\",\"sourceId\":\"n3\",\"sourcePort\":\"key3\",\"targetId\":\"n4\",\"targetPort\":\"in\"},{\"id\":\"e6\",\"sourceId\":\"n3\",\"sourcePort\":\"key0\",\"targetId\":\"n4\",\"targetPort\":\"in\"},{\"id\":\"e7\",\"sourceId\":\"n3\",\"sourcePort\":\"timeout\",\"targetId\":\"n4\",\"targetPort\":\"in\"},{\"id\":\"e8\",\"sourceId\":\"n4\",\"sourcePort\":\"answered\",\"targetId\":\"n5\",\"targetPort\":\"in\"},{\"id\":\"e9\",\"sourceId\":\"n4\",\"sourcePort\":\"overflow\",\"targetId\":\"n6\",\"targetPort\":\"in\"},{\"id\":\"e10\",\"sourceId\":\"n4\",\"sourcePort\":\"abandoned\",\"targetId\":\"n6\",\"targetPort\":\"in\"},{\"id\":\"e11\",\"sourceId\":\"n5\",\"sourcePort\":\"success\",\"targetId\":\"n6\",\"targetPort\":\"in\"},{\"id\":\"e12\",\"sourceId\":\"n5\",\"sourcePort\":\"fail\",\"targetId\":\"n6\",\"targetPort\":\"in\"}]}"
        ));
    }

    public static List<IvrTemplate> getAllTemplates() {
        return new ArrayList<>(TEMPLATES.values());
    }

    /**
     * Resolves the closest IVR template matching keywords in domain/objective/prompt content.
     */
    public static IvrTemplate getClosestTemplate(String domainOrObjective) {
        if (domainOrObjective == null || domainOrObjective.isBlank()) {
            return TEMPLATES.get("generic"); // Fix 9b: neutral fallback instead of hospitality
        }

        String search = domainOrObjective.toLowerCase().trim();

        // Exact key match first (e.g. domain = "banking", "healthcare", "education" etc.)
        for (Map.Entry<String, IvrTemplate> entry : TEMPLATES.entrySet()) {
            if (search.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        // Broad semantic keyword fallbacks
        if (search.contains("bank") || search.contains("card") || search.contains("finance") || search.contains("loan")) {
            return TEMPLATES.get("banking");
        }
        if (search.contains("hospital") || search.contains("clinic") || search.contains("doctor") || search.contains("patient") || search.contains("medical") || search.contains("health")) {
            return TEMPLATES.get("healthcare");
        }
        if (search.contains("hotel") || search.contains("concierge") || search.contains("room service") || search.contains("stay") || search.contains("resort")) {
            return TEMPLATES.get("hospitality");
        }
        if (search.contains("phone") || search.contains("telecom") || search.contains("billing") || search.contains("mobile") || search.contains("sim")) {
            return TEMPLATES.get("telecom");
        }
        if (search.contains("permit") || search.contains("tax") || search.contains("government") || search.contains("municipal") || search.contains("city")) {
            return TEMPLATES.get("government");
        }
        if (search.contains("pizza") || search.contains("food") || search.contains("restaurant") || search.contains("dining") || search.contains("order") || search.contains("cafe") || search.contains("catering")) {
            return TEMPLATES.get("restaurant");
        }
        if (search.contains("flight") || search.contains("airline") || search.contains("baggage") || search.contains("ticket")) {
            return TEMPLATES.get("airline");
        }
        if (search.contains("retail") || search.contains("store") || search.contains("shop") || search.contains("order status") || search.contains("refund")) {
            return TEMPLATES.get("retail");
        }
        if (search.contains("insurance") || search.contains("claim") || search.contains("policy") || search.contains("coverage")) {
            return TEMPLATES.get("insurance");
        }
        // Fix 9b: university / campus / education keywords
        if (search.contains("university") || search.contains("campus") || search.contains("college") || search.contains("admissions") || search.contains("enrollment") || search.contains("financial aid") || search.contains("student") || search.contains("faculty") || search.contains("registrar") || search.contains("helpline") || search.contains("academic")) {
            return TEMPLATES.get("education");
        }

        // Fix 9b: Return neutral generic template as catch-all — prevents silently returning
        // hotel-specific content ("Concierge Welcome", "Front Desk Transfer") for unrelated domains.
        logger.warn("[IvrTemplateRegistry] No template matched for domain '{}'. Using generic neutral fallback.", domainOrObjective);
        return TEMPLATES.get("generic");
    }
}
