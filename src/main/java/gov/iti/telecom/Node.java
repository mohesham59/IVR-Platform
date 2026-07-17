package gov.iti.telecom;

public class Node {
    // Core identity
    private String id; // Unique node identifier (e.g., "welcome", "main_menu")
    private String type; // Node type: "play", "menu", "form", "transfer", "database", "condition",
                         // "input", "goto", "end", "voicemail", "queue"
    private String next; // Default next node ID (fallback)

    // Audio / Prompt
    private String prompt; // TTS text or audio filename (e.g., "welcome_restaurant.wav")
    private String promptType; // "audio" | "tts" | "dynamic" (from variable)
    private List<String> promptVars; // Variables to inject into dynamic prompts

    // Menu-specific
    private Map<String, String> choices; // DTMF digit → next node ID (e.g., {"1": "reservations", "2": "hours"})
    private int timeout; // Milliseconds to wait for input (default 5000)
    private int maxDigits; // Max digits to collect (default 1 for menus)
    private String invalidNode; // Node to jump to on invalid input
    private String timeoutNode; // Node to jump to on timeout/no input

    // Form / Input-specific
    private List<Field> fields; // Ordered list of fields to collect

    // Transfer-specific
    private String destination; // SIP extension, PSTN number, or queue name (e.g., "SIP/101", "PJSIP/200")
    private int transferTimeout; // Ring timeout in seconds
    private String transferFailureNode; // Where to go if transfer fails

    // Condition-specific
    private String conditionVar; // Variable to evaluate (e.g., "${business_hours}")
    private Map<String, String> branches; // Value → next node ID (e.g., {"open": "main_menu", "closed": "after_hours"})
    private String defaultBranch; // Fallback if no match

    // Database / API-specific
    private String queryType; // "sql" | "http" | "ldap"
    private String query; // SQL query or HTTP endpoint
    private Map<String, String> queryParams; // Parameters to bind
    private String resultVar; // Variable to store result in
    private String successNode; // Next node on success
    private String failureNode; // Next node on failure

    // Queue-specific
    private String queueName; // Asterisk queue name
    private String queueOptions; // Queue options string
    private String queueTimeoutNode; // Where to go if queue timeout

    // Voicemail-specific
    private String voicemailBox; // Voicemail box extension
    private String voicemailEmail; // Optional email notification
    private String voicemailGreeting; // Custom greeting audio

    // Retry logic
    private int maxRetries; // Max attempts before giving up (default 3)
    private String retryNode; // Node to jump to for retry (usually self or prompt)

    // Timing
    private int interDigitTimeout; // Timeout between digits (default 3000ms)

    // Barge-in / Interrupt
    private boolean allowBargeIn; // Allow user to interrupt prompt with DTMF

    // Logging / Analytics
    private boolean logNode; // Whether to log this node visit
    private String tag; // Custom tag for analytics (e.g., "conversion_point")

    // Getters and setters...
}