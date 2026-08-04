package com.nexusivr.ai.util;

/**
 * Utility for sanitizing session titles and flow names derived from VoiceXML or user input.
 * <p>
 * Ensures titles:
 * <ul>
 *   <li>Have all escaped newline sequences ({@code \n}, {@code \r\n}, {@code \r}) replaced with space</li>
 *   <li>Have all literal newline/carriage return control characters replaced with space</li>
 *   <li>Have multiple consecutive whitespace characters collapsed into a single space</li>
 *   <li>Are stripped of leading and trailing whitespace</li>
 * </ul>
 */
public class TitleSanitizer {

    /**
     * Sanitizes a raw title string by removing newline artifacts and normalizing whitespace.
     *
     * @param input the raw title string, may be null or contain newlines/whitespace
     * @return clean, single-line title string, or empty string if input was null/blank
     */
    public static String sanitize(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String s = input;
        // Replace literal escaped backslash sequences (e.g. "\\r\\n", "\\n", "\\r")
        s = s.replace("\\r\\n", " ").replace("\\n", " ").replace("\\r", " ");
        // Replace actual control character newlines and carriage returns
        s = s.replace("\r", " ").replace("\n", " ");
        // Collapse multiple whitespace characters and trim leading/trailing space
        return s.replaceAll("\\s+", " ").trim();
    }
}
