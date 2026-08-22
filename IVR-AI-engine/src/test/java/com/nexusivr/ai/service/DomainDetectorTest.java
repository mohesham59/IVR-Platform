package com.nexusivr.ai.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DomainDetectorTest {

    @AfterEach
    void tearDown() {
        // Clean up any test session memory
    }

    // ─── Existing regression tests ────────────────────────────────────────────

    @Test
    void testTechnicalSupportFlowIsNotDetectedAsRestaurant() {
        String flowContent = "Start Menu L1 support Technical support Billing inquiries Product info End Call";
        String domain = DomainDetector.detect(flowContent);
        assertEquals("technical_support", domain,
                "Technical support flow with menu node must detect technical_support, NOT restaurant or banking");
    }

    @Test
    void testVoiceXmlWithMenuTagDoesNotTriggerRestaurant() {
        String vxmlWithMenu = "<vxml version=\"2.1\"><form id=\"menu\"><prompt>Please select an option: 1 for Technical support, 2 for Billing</prompt></form></vxml>";
        String domain = DomainDetector.detect(vxmlWithMenu);
        assertNotEquals("restaurant", domain,
                "VoiceXML containing XML menu tag must NOT trigger restaurant domain");
        assertEquals("technical_support", domain);
    }

    @Test
    void testSessionDomainPersistenceAndReuse() {
        UUID sessionId = UUID.randomUUID();
        SessionMemoryStore.setDomain(sessionId, "technical_support");

        String retrieved = SessionMemoryStore.getDomain(sessionId);
        assertEquals("technical_support", retrieved,
                "SessionMemoryStore must persist and return stored domain");

        SessionMemoryStore.clear(sessionId);
        assertNull(SessionMemoryStore.getDomain(sessionId));
    }

    // ─── Issue regression: hotel concierge → was wrongly classified as banking ─

    /**
     * Regression test for production bug: "Create a professional hotel concierge IVR.
     * Include departments for Reservations, Front Desk, Room Service, Housekeeping,
     * and Concierge" was classified as 'banking' instead of 'hospitality'.
     */
    @Test
    void testHotelConciergeIvrIsDetectedAsHospitality() {
        String prompt = "Create a professional hotel concierge IVR. " +
                "Include departments for Reservations, Front Desk, Room Service, " +
                "Housekeeping, and Concierge Services. The guests should be greeted " +
                "warmly and directed to the appropriate department.";
        String domain = DomainDetector.detect(prompt);
        assertEquals("hospitality", domain,
                "Hotel concierge IVR prompt must detect 'hospitality', NOT 'banking'. " +
                "Was misclassified as banking due to false substring matches in old code.");
    }

    @Test
    void testHotelResortIsHospitality() {
        String prompt = "Design an IVR for a luxury resort. Departments: front desk, housekeeping, concierge, valet.";
        assertEquals("hospitality", DomainDetector.detect(prompt));
    }

    @Test
    void testHotelCheckInCheckOutIsHospitality() {
        String prompt = "Hotel IVR for guests. Options: check-in, check-out, room service, front desk.";
        assertEquals("hospitality", DomainDetector.detect(prompt));
    }

    // ─── Whole-word boundary tests ────────────────────────────────────────────

    /**
     * "checking" (banking keyword in old code) must NOT match inside "check-in" or "checking out".
     */
    @Test
    void testCheckInPhraseDoesNotMatchCheckingAccount() {
        // "check-in" in prompt must NOT trigger "checking account" banking keyword
        assertFalse(DomainDetector.containsWholePhrase("check-in available", "checking account"),
                "'check-in' must not match 'checking account'");
    }

    @Test
    void testBranchWordDoesNotMatchBankingKeywordInsideBranching() {
        // "branching" must not hit the old banking keyword "branch"
        // (banking no longer uses bare "branch" — it requires "bank" etc.)
        assertFalse(DomainDetector.containsWholePhrase("auto-reconnected 6 branching paths", "bank"),
                "'branching' must not match banking keyword 'bank'");
    }

    @Test
    void testWholeWordMatchWorksForSingleWordKeyword() {
        assertTrue(DomainDetector.containsWholePhrase("hotel concierge ivr", "hotel"),
                "'hotel' must match as whole word");
        assertFalse(DomainDetector.containsWholePhrase("hotelier concierge", "hotel"),
                "'hotelier' must NOT match keyword 'hotel' (no word boundary)");
    }

    // ─── Minimum confidence threshold ────────────────────────────────────────

    @Test
    void testVagueOneWordPromptFallsBackToGeneric() {
        // Only a single weak keyword match — below MIN_CONFIDENCE=2, must fall back
        String domain = DomainDetector.detect("design IVR");
        assertEquals("generic", domain,
                "A vague prompt with no domain-specific keywords must return 'generic'");
    }

    @Test
    void testBlankPromptReturnsGeneric() {
        assertEquals("generic", DomainDetector.detect(""));
        assertEquals("generic", DomainDetector.detect(null));
        assertEquals("generic", DomainDetector.detect("   "));
    }

    // ─── Cross-domain collision audit ────────────────────────────────────────

    @Test
    void testBankingIvrPromptIsDetectedAsBanking() {
        String prompt = "Design a banking IVR. Include account balance, ATM locator, mortgage services, and wire transfer.";
        assertEquals("banking", DomainDetector.detect(prompt),
                "Banking IVR prompt must detect 'banking'");
    }

    @Test
    void testRestaurantIvrIsNotBanking() {
        String prompt = "Restaurant reservation IVR. Press 1 for takeout, press 2 for dining reservations, press 3 for chef specials.";
        String domain = DomainDetector.detect(prompt);
        assertNotEquals("banking", domain,
                "Restaurant IVR must NOT be detected as banking");
        assertEquals("restaurant", domain);
    }

    @Test
    void testAirlineIvrIsNotHospitality() {
        String prompt = "Airline IVR. Options: flight status, frequent flyer account, baggage services, departure gates.";
        String domain = DomainDetector.detect(prompt);
        assertNotEquals("hospitality", domain,
                "Airline IVR must NOT be detected as hospitality");
        assertEquals("airline", domain);
    }

    @Test
    void testHealthcareIvrIsDetectedAsHealthcare() {
        String prompt = "Design a clinic IVR. Departments: triage, prescription refill, appointment scheduling, nurse line, emergency room.";
        assertEquals("healthcare", DomainDetector.detect(prompt));
    }

    @Test
    void testInsuranceIvrIsNotBanking() {
        String prompt = "Insurance IVR. File a claim, check policy details, speak to an adjuster, or pay your premium.";
        String domain = DomainDetector.detect(prompt);
        assertNotEquals("banking", domain,
                "Insurance IVR must NOT be detected as banking");
        assertEquals("insurance", domain);
    }

    @Test
    void testTelecomIvrIsDetectedAsTelecom() {
        String prompt = "Telecom IVR. Options: SIM activation, broadband support, roaming services, landline issues.";
        assertEquals("telecom", DomainDetector.detect(prompt));
    }

    @Test
    void testEducationIvrIsDetectedAsEducation() {
        String prompt = "University IVR. Departments: admissions, registrar, financial aid, campus support.";
        assertEquals("education", DomainDetector.detect(prompt));
    }

    @Test
    void testGovernmentIvrIsDetectedAsGovernment() {
        String prompt = "Municipal government IVR. Services: passport renewal, DMV, county clerk, voting information.";
        assertEquals("government", DomainDetector.detect(prompt));
    }

    @Test
    void testArabicGovernmentKeywordsAreDetectedAsGovernment() {
        String prompt = "نظام الرد الآلي في الجوازات للاستعلام عن جوازات السفر والمعاملات في الهيئة";
        assertEquals("government", DomainDetector.detect(prompt));
    }

    @Test
    void testRetailIvrIsDetectedAsRetail() {
        String prompt = "Retail IVR. Options: order status, return and refund, store hours, ecommerce support.";
        assertEquals("retail", DomainDetector.detect(prompt));
    }

    /**
     * 'coverage' appears in both telecom and insurance keyword sets in old versions.
     * Audit: a clear insurance prompt must NOT be misclassified as telecom.
     */
    @Test
    void testInsuranceCoveragePromptIsNotTelecom() {
        String prompt = "Design an insurance IVR with deductible inquiries, liability coverage, and adjuster dispatch.";
        String domain = DomainDetector.detect(prompt);
        assertNotEquals("telecom", domain,
                "'coverage' in an insurance context must NOT match telecom domain");
        assertEquals("insurance", domain);
    }

    /**
     * 'policy' appears in both insurance and government contexts.
     * A government prompt with ordinance and legislation must not be insurance.
     */
    @Test
    void testGovernmentPolicyPromptIsNotInsurance() {
        String prompt = "Government IVR. Options: review municipal ordinance, federal legislation updates, DMV services.";
        String domain = DomainDetector.detect(prompt);
        assertNotEquals("insurance", domain,
                "Government IVR with ordinance/legislation must not be classified as insurance");
        assertEquals("government", domain);
    }

    @Test
    void testEgyptianArabicDialectDetectionForEachDomain() {
        // Healthcare
        assertEquals("healthcare", DomainDetector.detect("عايز مستشفى قريبة أو دكتور كويس أحجز عنده كشف"));
        assertEquals("healthcare", DomainDetector.detect("عندي مشكلة ومحتاج حجز معاد في العيادة وعايز صيدلية تجيب الروشتة"));

        // Education
        assertEquals("education", DomainDetector.detect("عايز أسجل في الكلية وأشوف شؤون الطلاب عشان المحاضرة"));
        assertEquals("education", DomainDetector.detect("تقديم جامعة القاهرة والمصاريف والمنهج الدراسي للطلبة"));

        // Insurance
        assertEquals("insurance", DomainDetector.detect("عايز أعمل تأمين على العربية وأدفع القسط السنوي للبوليصة"));
        assertEquals("insurance", DomainDetector.detect("تقديم مطالبة تعويض عن حادثة لشركة التأمين"));

        // Government
        assertEquals("government", DomainDetector.detect("تجديد جواز السفر في مصلحة الجوازات أو السجل المدني"));
        assertEquals("government", DomainDetector.detect("عايز أروح الشهر العقاري عشان توثيق وتراخيص المرور والضرائب"));

        // Airline
        assertEquals("airline", DomainDetector.detect("حجز رحلة طيران وميعاد الطيارة وتذاكر السفر للمطار"));
        assertEquals("airline", DomainDetector.detect("استعلام عن شنط السفر في صالة الوصول بمطار القاهرة"));

        // Hospitality
        assertEquals("hospitality", DomainDetector.detect("حجز أوضة في فندق خمس نجوم مع روم سيرفيس"));
        assertEquals("hospitality", DomainDetector.detect("لوكاندة كويسة في الإسكندرية وعايز ريسبشن عشان التشيك إن"));

        // Restaurant
        assertEquals("restaurant", DomainDetector.detect("عايز أطلب أوردر دليفري من مطعم سمك"));
        assertEquals("restaurant", DomainDetector.detect("حجز ترابيزة لعيلة في مطعم سوري ومينيو الأكل"));

        // Retail
        assertEquals("retail", DomainDetector.detect("عملت شوبينج من المحل وعايز مرتجع أو استرجاع للفاتورة"));
        assertEquals("retail", DomainDetector.detect("شراء منتج جديد من الدكان وفيه ضمان سنة"));

        // Telecom
        assertEquals("telecom", DomainDetector.detect("عايز أشحن رصيد خط اتصالات عشان باقة النت"));
        assertEquals("telecom", DomainDetector.detect("شريحة خط وباقة المكالمات والانترنت"));

        // Technical Support
        assertEquals("technical_support", DomainDetector.detect("كلم الدعم الفني عشان السيستم عطلان والشبكة وقعت"));
        assertEquals("technical_support", DomainDetector.detect("عندي مشكلة في اللابتوب وبايظ ومش شغال ومحتاج صيانة"));

        // Banking
        assertEquals("banking", DomainDetector.detect("عايز أسحب فلوس من البنك أو أعمل تحويل من حسابي"));
        assertEquals("banking", DomainDetector.detect("طلب قرض من البنك والاستعلام عن كشف حساب الكارت"));
    }
}
