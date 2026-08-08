package com.nexusivr.ai.service;

import com.nexusivr.ai.dto.CdrRecord;
import com.nexusivr.ai.dto.CdrSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads call records from the Asterisk CDR CSV log ({@code Master.csv}) and
 * builds list + aggregate analytics for the web dashboards.
 *
 * <p>The file is written by Asterisk's {@code cdr_csv} backend and shared with the
 * engine container via a bind mount ({@code ./asterisk-cdr}).
 */
public class CdrService {

    private static final Logger logger = LoggerFactory.getLogger(CdrService.class);

    private static final DateTimeFormatter START_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("MMM dd");

    private final Path cdrFile;

    public CdrService() {
        this(Paths.get(System.getenv().getOrDefault("CDR_CSV_PATH", "/var/log/asterisk/cdr-csv/Master.csv")));
    }

    public CdrService(Path cdrFile) {
        this.cdrFile = cdrFile;
    }

    /** Returns the most recent CDR records, newest first. */
    public List<CdrRecord> getRecentCalls(int limit) {
        List<CdrRecord> records = readAll();
        records.sort(Comparator.comparing(CdrRecord::getStart, Comparator.nullsLast(String::compareTo)).reversed());
        if (records.size() > limit) {
            return new ArrayList<>(records.subList(0, limit));
        }
        return records;
    }

    /** Builds aggregate KPIs and daily/hourly series from the CDR log. */
    public CdrSummary getSummary() {
        List<CdrRecord> records = readAll();

        int total = records.size();
        int answered = 0;
        long durationSum = 0;
        long billsecSum = 0;
        Map<LocalDate, CdrSummary.CdrDayBucket> dayMap = new LinkedHashMap<>();
        Map<Integer, CdrSummary.CdrHourBucket> hourMap = new HashMap<>();

        for (CdrRecord r : records) {
            boolean isAnswered = "ANSWERED".equalsIgnoreCase(r.getDisposition());
            if (isAnswered) {
                answered++;
            }
            durationSum += r.getDurationSec();
            billsecSum += r.getBillsec();

            LocalDateTime start = parseStart(r.getStart());
            if (start != null) {
                LocalDate day = start.toLocalDate();
                CdrSummary.CdrDayBucket bucket = dayMap.computeIfAbsent(day, d -> new CdrSummary.CdrDayBucket(DAY_LABEL.format(d), 0, 0, 0));
                bucket.setCalls(bucket.getCalls() + 1);
                if (isAnswered) {
                    bucket.setAnswered(bucket.getAnswered() + 1);
                } else {
                    bucket.setAbandoned(bucket.getAbandoned() + 1);
                }

                int hour = start.getHour();
                CdrSummary.CdrHourBucket hb = hourMap.computeIfAbsent(hour, h -> new CdrSummary.CdrHourBucket(h, 0));
                hb.setCalls(hb.getCalls() + 1);
            }
        }

        int abandoned = total - answered;
        double answeredRate = total > 0 ? Math.round(answered * 1000.0 / total) / 10.0 : 0.0;
        double abandonedRate = total > 0 ? Math.round(abandoned * 1000.0 / total) / 10.0 : 0.0;
        double avgDuration = total > 0 ? Math.round(durationSum * 10.0 / total) / 10.0 : 0.0;
        double avgBillsec = total > 0 ? Math.round(billsecSum * 10.0 / total) / 10.0 : 0.0;

        List<CdrSummary.CdrDayBucket> daily = new ArrayList<>(dayMap.values());
        daily.sort(Comparator.comparing(CdrSummary.CdrDayBucket::getDay));

        List<CdrSummary.CdrHourBucket> hourly = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            CdrSummary.CdrHourBucket hb = hourMap.get(h);
            hourly.add(hb != null ? hb : new CdrSummary.CdrHourBucket(h, 0));
        }

        return new CdrSummary(total, answered, abandoned, answeredRate, abandonedRate,
                avgDuration, avgBillsec, daily, hourly);
    }

    private List<CdrRecord> readAll() {
        if (cdrFile == null || !Files.exists(cdrFile)) {
            return Collections.emptyList();
        }
        try {
            List<String> lines = Files.readAllLines(cdrFile);
            return parse(lines);
        } catch (IOException e) {
            logger.warn("Could not read CDR log {}: {}", cdrFile, e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<CdrRecord> parse(List<String> lines) {
        if (lines.isEmpty()) {
            return Collections.emptyList();
        }

        // Header row: map column name -> index.
        Map<String, Integer> header = parseHeader(lines.get(0));
        if (header.isEmpty() || !header.containsKey("src")) {
            logger.warn("CDR log {} missing expected header row", cdrFile);
            return Collections.emptyList();
        }

        List<CdrRecord> records = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line == null || line.trim().isEmpty()) {
                continue;
            }
            Map<String, String> row = parseRow(line, header);
            if (row.isEmpty()) {
                continue;
            }
            String disposition = row.getOrDefault("disposition", "").trim();
            CdrRecord rec = new CdrRecord(
                    row.getOrDefault("uniqueid", "").trim(),
                    row.getOrDefault("src", "").trim(),
                    row.getOrDefault("dst", "").trim(),
                    row.getOrDefault("start", "").trim(),
                    row.getOrDefault("answer", "").trim(),
                    parseLong(row.get("duration")),
                    parseLong(row.get("billsec")),
                    disposition,
                    mapStatus(disposition)
            );
            records.add(rec);
        }
        return records;
    }

    /** Parses the header row into a column name -> index map. */
    private Map<String, Integer> parseHeader(String line) {
        Map<String, Integer> header = new HashMap<>();
        String[] parts = line.split(",", -1);
        for (int i = 0; i < parts.length; i++) {
            String name = unquote(parts[i]).trim();
            if (!name.isEmpty()) {
                header.put(name, i);
            }
        }
        return header;
    }

    /**
     * Parses one data row using the header index map, returning column name ->
     * value. Handles the quoted output produced by cdr_csv.
     */
    private Map<String, String> parseRow(String line, Map<String, Integer> header) {
        Map<String, String> row = new HashMap<>();
        String[] parts = line.split(",", -1);
        for (Map.Entry<String, Integer> entry : header.entrySet()) {
            int idx = entry.getValue();
            String value = idx < parts.length ? unquote(parts[idx]) : "";
            row.put(entry.getKey(), value);
        }
        return row;
    }

    private String unquote(String value) {
        if (value == null) {
            return "";
        }
        String v = value.trim();
        if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
            v = v.substring(1, v.length() - 1);
        }
        return v.replace("\"\"", "\"");
    }

    private long parseLong(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private LocalDateTime parseStart(String start) {
        if (start == null || start.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(start.trim(), START_FORMAT);
        } catch (Exception e) {
            return null;
        }
    }

    private String mapStatus(String disposition) {
        String d = disposition == null ? "" : disposition.trim().toUpperCase();
        return switch (d) {
            case "ANSWERED" -> "Answered";
            case "NO ANSWER", "CHANUNAVAIL", "CANCEL" -> "No Answer";
            case "BUSY" -> "Busy";
            case "FAILED", "CONGESTION" -> "Failed";
            default -> d.isEmpty() ? "Unknown" : disposition.trim();
        };
    }
}
