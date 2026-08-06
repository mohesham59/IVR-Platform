package com.nexusivr.ai.dao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VoicePromptDao {

    private static final Logger logger = LoggerFactory.getLogger(VoicePromptDao.class);

    private static final String FIND_ID_BY_NAME_SQL = "SELECT id FROM voice_prompts WHERE name = ?";

    private static final String UPDATE_SQL = """
        UPDATE voice_prompts SET 
            language = ?, duration = ?, type = ?, created_by = ?, file_path = ?, size_bytes = ?, updated_at = CURRENT_TIMESTAMP
        WHERE name = ?
        """;

    private static final String INSERT_SQL = """
        INSERT INTO voice_prompts (name, language, duration, type, created_by, file_path, size_bytes)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """;

    private static final String DELETE_SQL = "DELETE FROM voice_prompts WHERE name = ?";
    
    private static final String FIND_ALL_SQL = "SELECT name, language, duration, type, created_by, file_path, size_bytes, updated_at FROM voice_prompts ORDER BY updated_at DESC";
    
    private static final String FIND_BY_NAME_SQL = "SELECT file_path FROM voice_prompts WHERE name = ?";

    public void upsert(String name, String language, String duration, String type, String createdBy, String filePath, long sizeBytes) throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            boolean exists = false;
            try (PreparedStatement ps = conn.prepareStatement(FIND_ID_BY_NAME_SQL)) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) exists = true;
                }
            }

            if (exists) {
                try (PreparedStatement ps = conn.prepareStatement(UPDATE_SQL)) {
                    ps.setString(1, language);
                    ps.setString(2, duration);
                    ps.setString(3, type);
                    ps.setString(4, createdBy);
                    ps.setString(5, filePath);
                    ps.setLong(6, sizeBytes);
                    ps.setString(7, name);
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
                    ps.setString(1, name);
                    ps.setString(2, language);
                    ps.setString(3, duration);
                    ps.setString(4, type);
                    ps.setString(5, createdBy);
                    ps.setString(6, filePath);
                    ps.setLong(7, sizeBytes);
                    ps.executeUpdate();
                }
            }
        }
    }

    public void deleteByName(String name) throws SQLException {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(DELETE_SQL)) {
            ps.setString(1, name);
            ps.executeUpdate();
        }
    }


    public List<Map<String, Object>> findAll() {
        List<Map<String, Object>> prompts = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_ALL_SQL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> p = new HashMap<>();
                p.put("name", rs.getString("name"));
                p.put("language", rs.getString("language"));
                p.put("duration", rs.getString("duration"));
                p.put("type", rs.getString("type"));
                p.put("createdBy", rs.getString("created_by"));
                p.put("filePath", rs.getString("file_path"));
                p.put("sizeBytes", rs.getLong("size_bytes"));
                p.put("updatedAt", rs.getTimestamp("updated_at"));
                prompts.add(p);
            }
        } catch (SQLException e) {
            logger.error("Error retrieving voice prompts", e);
        }
        return prompts;
    }
    
    public String getFilePathByName(String name) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_BY_NAME_SQL)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("file_path");
                }
            }
        } catch (SQLException e) {
            logger.error("Error retrieving voice prompt path", e);
        }
        return null;
    }
}
