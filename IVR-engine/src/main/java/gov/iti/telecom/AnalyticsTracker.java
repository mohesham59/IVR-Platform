package gov.iti.telecom;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;

/**
 * Tracks IVR call events and saves the call record to the PostgreSQL database.
 */
public class AnalyticsTracker {

    private final String callId;
    private final String callerNumber;
    private final Instant startTime;
    private final JsonArray events;
    private String recordingUrl;

    public AnalyticsTracker(String callId, String callerNumber) {
        this.callId = callId;
        this.callerNumber = callerNumber;
        this.startTime = Instant.now();
        this.events = new JsonArray();
        
        // Log CALL_START
        addEvent("CALL_START", null);
    }

    public void addEvent(String type, String data) {
        JsonObject event = new JsonObject();
        event.addProperty("type", type);
        if (data != null) {
            event.addProperty("data", data);
        }
        event.addProperty("timestamp", Instant.now().toString());
        this.events.add(event);
    }

    public void setRecordingUrl(String url) {
        this.recordingUrl = url;
    }

    public void saveToDatabase() {
        // Log CALL_END
        addEvent("CALL_END", null);
        Instant endTime = Instant.now();
        int duration = (int) java.time.Duration.between(startTime, endTime).getSeconds();

        String dbUrl = getEnvOrDefault("DATABASE_URL", "");
        String dbUser = getEnvOrDefault("DATABASE_USER", "");
        String dbPass = getEnvOrDefault("DATABASE_PASSWORD", "");

        if (dbUrl.isEmpty()) {
            System.err.println("[AnalyticsTracker] Warning: No DATABASE_URL provided. Cannot save analytics.");
            return;
        }

        String sql = "INSERT INTO call_analytics (call_id, caller_number, start_time, end_time, duration, events, recording_url) " +
                     "VALUES (?, ?, ?, ?, ?, ?::jsonb, ?)";

        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, callId);
            pstmt.setString(2, callerNumber);
            pstmt.setTimestamp(3, Timestamp.from(startTime));
            pstmt.setTimestamp(4, Timestamp.from(endTime));
            pstmt.setInt(5, duration);
            pstmt.setString(6, events.toString());
            pstmt.setString(7, recordingUrl);

            pstmt.executeUpdate();
            System.out.println("[AnalyticsTracker] Successfully saved call analytics for call: " + callId);
        } catch (Exception e) {
            System.err.println("[AnalyticsTracker] Failed to save call analytics: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String getEnvOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.trim().isEmpty()) {
            value = System.getProperty(name, defaultValue);
        }
        return value;
    }
}
