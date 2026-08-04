package com.nexusivr.ai.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DepartmentExtractor {

    private static final Pattern DEPT_HEADER_PATTERN = Pattern.compile(
            "(?i)(?:departments|options|branches|services|menu)\\s+(?:for|of|including|with|like)?\\s*([^\\.\\!\\?]+)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern INCLUDE_HEADER_PATTERN = Pattern.compile(
            "(?i)(?:include|including|with)\\s+(?:departments|options|branches|services)?\\s*(?:for|of|including|with|like)?\\s*([^\\.\\!\\?]+)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern NUMBERED_OPTIONS = Pattern.compile(
            "(?i)(?:press|option|digit|choice|\\b)?\\s*([0-9]+)\\s*(?:for|:|-|\\))\\s*([A-Za-z][A-Za-z\\s&/]{1,30}?)(?=\\s*\\d+[\\s\\:\\-\\)]|\\s*,|\\s*$|\\s*\\.|\\s*main|\\s*menu)",
            Pattern.CASE_INSENSITIVE
    );

    public static List<String> extractDepartments(String prompt) {
        List<String> results = new ArrayList<>();
        if (prompt == null || prompt.isBlank()) {
            return results;
        }

        // 1. Try explicit header matching ("departments for X, Y, Z" or "include X, Y, Z")
        Matcher deptMatcher = DEPT_HEADER_PATTERN.matcher(prompt);
        if (deptMatcher.find()) {
            parseListString(deptMatcher.group(1), results);
        }

        if (results.size() < 2) {
            results.clear();
            Matcher includeMatcher = INCLUDE_HEADER_PATTERN.matcher(prompt);
            if (includeMatcher.find()) {
                parseListString(includeMatcher.group(1), results);
            }
        }

        if (results.size() >= 2) {
            return results;
        }

        // 2. Try non-greedy numbered options matching ("1-Billing 2-Roaming 3-SIM Support 4-Internet")
        results.clear();
        Matcher numMatcher = NUMBERED_OPTIONS.matcher(prompt);
        while (numMatcher.find()) {
            String dept = numMatcher.group(2).trim();
            dept = cleanDepartmentName(dept);
            if (isValidDepartmentName(dept) && !results.contains(dept)) {
                results.add(dept);
            }
        }

        return results;
    }

    private static void parseListString(String rawList, List<String> results) {
        if (rawList == null || rawList.isBlank()) return;
        String cleanedText = rawList;
        int numIndex = findFirstNumberedOptionIndex(cleanedText);
        if (numIndex >= 0) {
            cleanedText = cleanedText.substring(0, numIndex);
        }

        String[] parts = cleanedText.split("(?i),|\\band\\b|\\bor\\b|;");
        for (String part : parts) {
            String dept = cleanDepartmentName(part);
            if (isValidDepartmentName(dept) && !results.contains(dept)) {
                results.add(dept);
            }
        }
    }

    private static int findFirstNumberedOptionIndex(String text) {
        Matcher m = Pattern.compile("(?i)\\b\\d+[\\s\\:\\-\\)]\\s*[a-zA-Z]").matcher(text);
        if (m.find()) {
            return m.start();
        }
        return -1;
    }

    private static String cleanDepartmentName(String input) {
        if (input == null) return "";
        String cleaned = input.replaceAll("\\d+$", "").trim();
        cleaned = cleaned.replaceAll("(?i)^(include|including|departments|options|services|menu|for|with|and|or|press|choice|digit|select|to|speak|a|an|the|our)\\s+", "").trim();
        cleaned = cleaned.replaceAll("[\\.\\,\\!\\?;\"]", "").trim();
        cleaned = cleaned.replaceAll("\\s+\\d+$", "").trim();
        if (!cleaned.isBlank()) {
            String[] words = cleaned.split("\\s+");
            StringBuilder sb = new StringBuilder();
            for (String w : words) {
                if (!w.isBlank()) {
                    sb.append(Character.toUpperCase(w.charAt(0)))
                      .append(w.substring(1).toLowerCase())
                      .append(" ");
                }
            }
            return sb.toString().trim();
        }
        return cleaned;
    }

    private static boolean isValidDepartmentName(String name) {
        if (name == null || name.isBlank()) return false;
        if (name.length() < 2 || name.length() > 35) return false;
        String lower = name.toLowerCase();
        if (lower.equals("ivr") || lower.equals("flow") || lower.equals("system") || lower.equals("bot") || lower.equals("assistant") || lower.equals("department") || lower.equals("departments") || lower.equals("main menu") || lower.equals("options")) {
            return false;
        }
        if (name.matches("^\\d+$")) return false;
        return true;
    }
}
