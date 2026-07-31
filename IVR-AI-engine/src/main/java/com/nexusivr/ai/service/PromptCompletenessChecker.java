package com.nexusivr.ai.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fast heuristic check to estimate whether a user prompt is already sufficiently
 * specified for flow generation, or whether it would benefit from Pass 1 refinement.
 *
 * <p>This is a local, regex-based check — NOT an LLM call. The goal is to avoid
 * an unnecessary LLM round-trip for prompts that already contain enough detail.
 */
public class PromptCompletenessChecker {

    private static final Pattern NUMBERED_OPTIONS = Pattern.compile(
            "(?i)(press\\s+\\d+|\\d+[\\.\\)]\\s*[^\\n]{3,40}|option\\s+\\d+|menu\\s+item\\s*\\d+)"
    );
    private static final Pattern DEPARTMENT_NAMES = Pattern.compile(
            "(?i)(departments?|categories?|sections?|services?|teams?|groups?)\\s*(for|include|include:|are|:)"
    );
    private static final Pattern STANDARD_IVR_ELEMENTS = Pattern.compile(
            "(?i)(greeting|welcome|hours|closing|goodbye|transfer|escalat|error\\s*handling|noinput|nomatch|fallback)"
    );
    private static final Pattern EXPLICIT_MENU = Pattern.compile(
            "(?i)(press\\s+1\\s+for|press\\s+2\\s+for|press\\s+3\\s+for|1\\s*[-–]|2\\s*[-–]|3\\s*[-–])"
    );
    private static final Pattern VAGUE_PROMPT = Pattern.compile(
            "(?i)^(make\\s+me|create\\s+a|build\\s+a|generate\\s+a)\\s+an?\\s+ivr?\\s*$"
    );

    private PromptCompletenessChecker() {}

    /**
     * Returns true if the prompt appears sufficiently specified to skip Pass 1.
     */
    public static boolean isWellSpecified(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return false;
        }

        String text = prompt.trim();
        int length = text.length();

        Matcher vague = VAGUE_PROMPT.matcher(text);
        if (vague.find() && length < 150) {
            return false;
        }

        boolean hasNumberedOptions = NUMBERED_OPTIONS.matcher(text).find();
        boolean hasDepartments = DEPARTMENT_NAMES.matcher(text).find();
        boolean hasIvrElements = STANDARD_IVR_ELEMENTS.matcher(text).find();
        boolean hasExplicitMenu = EXPLICIT_MENU.matcher(text).find();

        int signals = 0;
        if (hasNumberedOptions) signals++;
        if (hasDepartments) signals++;
        if (hasExplicitMenu) signals++;
        if (hasIvrElements) signals++;
        if (length >= 200) signals++;

        return signals >= 2;
    }

    /**
     * Returns a human-readable reason for whether Pass 1 was triggered or skipped.
     */
    public static String getReason(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return "Pass 1 triggered: empty prompt";
        }

        String text = prompt.trim();
        if (text.length() < 50) {
            return "Pass 1 triggered: prompt too short";
        }

        Matcher vague = VAGUE_PROMPT.matcher(text);
        if (vague.find() && text.length() < 150) {
            return "Pass 1 triggered: vague prompt";
        }

        if (!isWellSpecified(text)) {
            return "Pass 1 triggered: missing department/menu details";
        }

        return "Pass 1 skipped: prompt already well-specified";
    }
}
