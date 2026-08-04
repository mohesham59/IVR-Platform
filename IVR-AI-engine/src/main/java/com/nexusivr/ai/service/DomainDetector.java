package com.nexusivr.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pure rule-based business domain detector.
 * <p>
 * Never calls the LLM. Uses deterministic keyword matching against the
 * user prompt to select one of the supported IVR domains. If nothing
 * matches, returns {@code generic} so the template registry can supply
 * a neutral fallback flow.
 * <p>
 * Domains are checked in priority order (most specific first).
 * Banking keywords are checked last among specific domains to prevent
 * false positives from generic financial terms appearing in other contexts.
 */
public class DomainDetector {

    private DomainDetector() {}
    private static final Logger logger =
            LoggerFactory.getLogger(DomainDetector.class);

    /**
     * Minimum score a domain must reach to be selected.
     * A single incidental keyword match (score=1) is not enough to assign a domain —
     * the prompt must contain at least 2 matching signals. This prevents accidental
     * single-word overlaps (e.g. "checking" from a hotel prompt) from winning.
     */
    private static final int MIN_CONFIDENCE = 2;

    // Order matters: more-specific / less-overlapping domains first.
    // Banking is intentionally placed near the end to avoid false positives
    // from generic terms like "card" or "account" appearing in other domains.
    private static final Map<String, String[]> DOMAIN_RULES = new LinkedHashMap<>();

    /**
     * High-weight (+3) keywords per domain — strong / unambiguous identifiers.
     * A keyword listed here gets +3 instead of +1 when matched.
     */
    private static final Map<String, String[]> HIGH_WEIGHT_KEYWORDS = new LinkedHashMap<>();

    static {
        // ── healthcare ──────────────────────────────────────────────────────
        DOMAIN_RULES.put("healthcare", new String[]{
                "hospital", "clinic", "doctor", "patient", "medical", "health",
                "pharmacy", "appointment", "triage", "nurse", "emergency room",
                "prescription", "healthcare", "health care", "urgent care",
                "diagnosis", "treatment", "surgery", "wellness",
                "عيادة", "عياده", "مستشفى", "مستشفه", "طبيب", "دكتور", "مريض", "صيدلية", "صيدليه",
                "طوارئ", "روشتة", "علاج", "جراحة", "جراحه", "حجز موعد", "مواعيد", "العيادات", "المستشفيات"
        });
        HIGH_WEIGHT_KEYWORDS.put("healthcare", new String[]{
                "hospital", "clinic", "healthcare", "triage", "prescription", "surgery",
                "عيادة", "عياده", "مستشفى", "مستشفه", "طوارئ", "روشتة"
        });

        // ── education ────────────────────────────────────────────────────────
        DOMAIN_RULES.put("education", new String[]{
                "university", "college", "campus", "admissions", "enrollment",
                "financial aid", "student services", "registrar", "academic",
                "professor", "degree", "transcript", "student", "school",
                "tuition", "lecture", "curriculum",
                "جامعة", "جامعه", "كلية", "كليه", "مدرسة", "مدرسه", "طالب", "طلاب", "تسجيل", "قبول", "دراسة", "المدرسة", "الجامعة"
        });
        HIGH_WEIGHT_KEYWORDS.put("education", new String[]{
                "university", "college", "campus", "registrar", "admissions",
                "جامعة", "جامعه", "كلية", "كليه", "مدرسة", "مدرسه"
        });

        // ── insurance ────────────────────────────────────────────────────────
        DOMAIN_RULES.put("insurance", new String[]{
                "insurance", "claim", "policy", "premium", "insure",
                "adjuster", "liability", "deductible", "beneficiary", "underwrite",
                "policyholder", "reimbursement",
                "تأمين", "تامين", "مطالبة", "مطالبه", "وثيقة", "وثيقه", "بوليصة", "بوليصه", "المؤمن"
        });
        HIGH_WEIGHT_KEYWORDS.put("insurance", new String[]{
                "insurance", "insure", "adjuster", "deductible", "underwrite",
                "تأمين", "تامين", "بوليصة", "بوليصه"
        });

        // ── government ───────────────────────────────────────────────────────
        DOMAIN_RULES.put("government", new String[]{
                "government", "permit", "tax", "municipal", "city", "county",
                "citizen", "public service", "county clerk", "dmv",
                "passport", "voting", "ordinance", "zoning", "federal",
                "legislation", "regulation",
                "حكومي", "حكومة", "حكومه", "جوازات", "بلدية", "بلديه", "مرور", "ترخيص", "رخصة", "رخصه"
        });
        HIGH_WEIGHT_KEYWORDS.put("government", new String[]{
                "government", "municipal", "dmv", "passport", "ordinance", "legislation",
                "جوازات", "بلدية", "بلديه", "مرور", "ترخيص"
        });

        // ── airline ──────────────────────────────────────────────────────────
        DOMAIN_RULES.put("airline", new String[]{
                "airline", "flight", "baggage", "boarding", "airport",
                "flight status", "itinerary", "frequent flyer",
                "departure", "arrival", "gate", "carry-on", "airfare",
                "plane ticket", "flight booking",
                "طيران", "رحلة", "رحله", "مطار", "تذكرة", "تذكره", "حجز طيران", "أمتعة", "امتعة", "طيارة"
        });
        HIGH_WEIGHT_KEYWORDS.put("airline", new String[]{
                "airline", "flight", "airport", "boarding", "airfare", "frequent flyer",
                "طيران", "مطار", "رحلة", "رحله"
        });

        // ── hospitality ──────────────────────────────────────────────────────
        DOMAIN_RULES.put("hospitality", new String[]{
                "hotel", "concierge", "room service", "resort", "check-in",
                "check in", "check-out", "check out", "checkout",
                "front desk", "housekeeping", "lodging", "inn", "motel",
                "suite", "hospitality", "reception", "valet",
                "guest services", "bell desk", "amenities",
                "reservations", "hotel reservation", "hotel booking",
                "spa", "bed and breakfast", "b&b",
                "فندق", "فنادق", "منتجع", "استقبال", "خدمة الغرف", "غرفة", "غرفه", "جناح", "نزيل", "حجز فندق"
        });
        HIGH_WEIGHT_KEYWORDS.put("hospitality", new String[]{
                "hotel", "concierge", "hospitality", "housekeeping",
                "front desk", "room service", "valet", "lodging",
                "guest services", "bell desk",
                "فندق", "منتجع", "خدمة الغرف"
        });

        // ── restaurant ───────────────────────────────────────────────────────
        DOMAIN_RULES.put("restaurant", new String[]{
                "restaurant", "pizza", "bistro", "takeout", "kitchen", "catering",
                "dining", "cafe", "food delivery", "takeaway",
                "restaurant menu", "food menu", "dining menu",
                "reservation", "hostess", "chef", "dish", "cuisine",
                "bakery", "diner", "eatery",
                "مطعم", "مطاعم", "بيتزا", "وجبات", "قائمة", "قائمه", "منيو", "طعام", "حجز طاولة", "توصيل", "سفري", "شاورما", "كافيه"
        });
        HIGH_WEIGHT_KEYWORDS.put("restaurant", new String[]{
                "restaurant", "pizza", "bistro", "dining", "takeout", "cuisine", "chef",
                "مطعم", "مطاعم", "بيتزا", "منيو", "توصيل"
        });

        // ── retail ───────────────────────────────────────────────────────────
        DOMAIN_RULES.put("retail", new String[]{
                "retail", "store", "shop", "order status", "refund", "return",
                "mall", "purchase", "product", "warranty",
                "ecommerce", "e-commerce", "shopping", "cart",
                "متجر", "محل", "متاجر", "طلب", "استرجاع", "منتج", "تسوق", "شراء", "ضمان"
        });
        HIGH_WEIGHT_KEYWORDS.put("retail", new String[]{
                "retail", "ecommerce", "e-commerce", "refund", "warranty",
                "متجر", "متاجر", "استرجاع", "ضمان"
        });

        // ── telecom ──────────────────────────────────────────────────────────
        DOMAIN_RULES.put("telecom", new String[]{
                "telecom", "mobile", "sim", "cellular", "roaming",
                "broadband", "internet provider", "cable tv", "calling plan",
                "landline", "voip", "data plan", "signal strength", "coverage area",
                "network operator", "phone plan",
                "اتصالات", "شريحة", "شريحه", "باقة", "باقه", "رصيد", "إنترنت", "انترنت", "تغطية", "شبكة", "هاتف"
        });
        HIGH_WEIGHT_KEYWORDS.put("telecom", new String[]{
                "telecom", "sim", "cellular", "roaming", "broadband", "voip", "landline",
                "اتصالات", "شريحة", "شريحه", "باقة", "باقه", "انترنت", "إنترنت"
        });

        // ── technical_support ────────────────────────────────────────────────
        DOMAIN_RULES.put("technical_support", new String[]{
                "technical support", "tech support", "it support",
                "helpdesk", "help desk", "service desk",
                "l1 support", "l2 support", "tier 1", "tier 2",
                "sysadmin", "troubleshoot", "troubleshooting",
                "network outage", "incident ticket", "bug report",
                "deployment", "api issue",
                "الدعم الفني", "دعم فني", "صيانة", "صيانه", "اعطال", "أعطال", "مشكلة", "عطل"
        });
        HIGH_WEIGHT_KEYWORDS.put("technical_support", new String[]{
                "technical support", "tech support", "it support",
                "helpdesk", "troubleshooting", "sysadmin",
                "الدعم الفني", "دعم فني", "اعطال", "أعطال"
        });

        // ── banking ──────────────────────────────────────────────────────────
        DOMAIN_RULES.put("banking", new String[]{
                "bank", "banking", "credit card", "debit card",
                "account balance", "atm", "mortgage", "investment portfolio",
                "checking account", "savings account", "pin number", "fraud alert",
                "wire transfer", "bank teller", "bank deposit", "withdrawal",
                "credit score", "loan application", "overdraft",
                "بنك", "بنوك", "مصرف", "رصيد", "حساب", "بطاقة", "بطاقه", "تمويل", "قرض", "سداد", "تحويل"
        });
        HIGH_WEIGHT_KEYWORDS.put("banking", new String[]{
                "bank", "banking", "mortgage", "atm", "overdraft",
                "wire transfer", "credit score", "loan application",
                "بنك", "مصرف", "قرض", "سداد"
        });
    }

    /**
     * Detects the business domain from a free-text user prompt using weighted keyword scoring.
     *
     * @param prompt the raw user prompt / business description
     * @return one of the supported domain keys, or {@code "generic"} when no domain
     *         scores at or above {@link #MIN_CONFIDENCE}.
     */
    public static String detect(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return "generic";
        }

        String lower = prompt.toLowerCase(java.util.Locale.ROOT)
                             .replace('\t', ' ')
                             .replace('\n', ' ')
                             .replace('\r', ' ');

        String bestDomain = "generic";
        int maxScore = 0;
        java.util.List<String> bestKeywords = new java.util.ArrayList<>();

        for (Map.Entry<String, String[]> entry : DOMAIN_RULES.entrySet()) {
            String domain = entry.getKey();
            int score = 0;
            java.util.List<String> matched = new java.util.ArrayList<>();

            String[] highWeightSet = HIGH_WEIGHT_KEYWORDS.getOrDefault(domain, new String[0]);
            java.util.Set<String> highWeightSetLookup = new java.util.HashSet<>(
                    java.util.Arrays.asList(highWeightSet));

            for (String keyword : entry.getValue()) {
                if (containsWholePhrase(lower, keyword)) {
                    matched.add(keyword);
                    score += highWeightSetLookup.contains(keyword) ? 3 : 1;
                }
            }

            if (score > maxScore) {
                maxScore = score;
                bestDomain = domain;
                bestKeywords = matched;
            }
        }

        String snippet = prompt.length() > 80 ? prompt.substring(0, 80) + "..." : prompt;

        if (maxScore >= MIN_CONFIDENCE) {
            logger.info("[DomainDetector] Matched domain='{}' (score={}, keywords={}) for prompt: '{}'",
                    bestDomain, maxScore, bestKeywords, snippet.replaceAll("\\s+", " "));
            return bestDomain;
        }

        logger.info("[DomainDetector] No domain matched with sufficient confidence (maxScore={}, threshold={}). " +
                        "Falling back to 'generic' for prompt: '{}'",
                maxScore, MIN_CONFIDENCE, snippet.replaceAll("\\s+", " "));
        return "generic";
    }

    /**
     * Returns {@code true} when {@code text} contains {@code phrase} as a whole-word match.
     */
    static boolean containsWholePhrase(String text, String phrase) {
        int idx = text.indexOf(phrase);
        if (idx < 0) return false;

        // Multi-word phrases or phrase containing non-alphanumeric chars
        if (phrase.contains(" ") || phrase.contains("-")) {
            return true;
        }

        // Check if phrase is Arabic
        boolean isArabicPhrase = isArabicChar(phrase.charAt(0));
        if (isArabicPhrase) {
            // For Arabic words, prepositions (ل, ب, ف, ك, و, ال) prefixing the word are common
            return true;
        }

        // Single-word ASCII phrases: enforce word boundaries.
        char before = idx > 0 ? text.charAt(idx - 1) : ' ';
        char after  = idx + phrase.length() < text.length()
                      ? text.charAt(idx + phrase.length()) : ' ';

        boolean boundaryBefore = !Character.isLetterOrDigit(before);
        boolean boundaryAfter  = !Character.isLetterOrDigit(after);
        return boundaryBefore && boundaryAfter;
    }

    private static boolean isArabicChar(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.ARABIC
                || block == Character.UnicodeBlock.ARABIC_SUPPLEMENT
                || block == Character.UnicodeBlock.ARABIC_EXTENDED_A
                || block == Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_A
                || block == Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_B;
    }
}