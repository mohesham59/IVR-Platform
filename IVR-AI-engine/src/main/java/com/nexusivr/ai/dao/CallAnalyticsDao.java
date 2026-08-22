package com.nexusivr.ai.dao;

import com.nexusivr.ai.model.CallAnalyticsRecord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class CallAnalyticsDao {

    public CallAnalyticsDao() {
    }

    public List<CallAnalyticsRecord> getAllCalls() {
        List<CallAnalyticsRecord> records = new ArrayList<>();
        String sql = "SELECT id, call_id, caller_number, start_time, end_time, duration, events, recording_url " +
                     "FROM call_analytics ORDER BY start_time DESC";
                     
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
             
            while (rs.next()) {
                CallAnalyticsRecord record = new CallAnalyticsRecord();
                record.setId(rs.getInt("id"));
                record.setCallId(rs.getString("call_id"));
                record.setCallerNumber(rs.getString("caller_number"));
                record.setStartTime(rs.getTimestamp("start_time"));
                record.setEndTime(rs.getTimestamp("end_time"));
                record.setDuration(rs.getInt("duration"));
                record.setEvents(rs.getString("events"));
                record.setRecordingUrl(rs.getString("recording_url"));
                records.add(record);
            }
        } catch (SQLException e) {
            System.err.println("Error fetching call analytics: " + e.getMessage());
        }
        return records;
    }
}
