package gov.iti.telecom;

public class Field {
    private String name; // Variable name (e.g., "res_date", "party_size")
    private String prompt; // Audio file or TTS text
    private String promptType; // "audio" | "tts"
    private int length; // Exact number of digits expected
    private int minLength; // Minimum digits (for variable length)
    private int maxLength; // Maximum digits
    private String type; // "digits" | "date" | "time" | "phone" | "currency" | "custom"
    private String validationRegex; // Regex for validation (e.g., "\\d{4}" for 4 digits)
    private String invalidPrompt; // Prompt to play on invalid input
    private int maxAttempts; // Max retries for this field
    private String defaultValue; // Default if no input provided
    private boolean required; // Whether field can be skipped
    private String nextOnEmpty; // Node to jump to if empty and not required

    // Getters and setters...
}