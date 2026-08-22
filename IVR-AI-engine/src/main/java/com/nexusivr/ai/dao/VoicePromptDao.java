package com.nexusivr.ai.dao;

import java.sql.*;
import java.util.*;

public class VoicePromptDao {

    public VoicePromptDao() {}

    public int getVoicePromptsCount(UUID tenantId) {
        String sql = "SELECT COUNT(*) FROM voice_prompts WHERE tenant_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setObject(1, tenantId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error counting voice prompts for tenant " + tenantId + ": " + e.getMessage());
        }
        return 0;
    }

    public boolean upsert(String name, String language, String duration, String type, String createdBy, String filePath, long sizeBytes, String promptText) {
        String sql = "INSERT INTO voice_prompts (tenant_id, name, language, duration, type, created_by, file_path, size_bytes) " +
                     "VALUES ('11111111-1111-1111-1111-111111111111', ?, ?, ?, ?, ?, ?, ?) " +
                     "ON CONFLICT (id) DO UPDATE SET duration = EXCLUDED.duration, updated_at = now()";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, name);
            pstmt.setString(2, language);
            pstmt.setString(3, duration);
            pstmt.setString(4, type);
            pstmt.setString(5, createdBy);
            pstmt.setString(6, filePath);
            pstmt.setLong(7, sizeBytes);

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error upserting voice prompt: " + e.getMessage());
            return false;
        }
    }

    public String getFilePathByName(String name) {
        String sql = "SELECT file_path FROM voice_prompts WHERE name = ? LIMIT 1";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, name);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("file_path");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting file path by name: " + e.getMessage());
        }
        return null;
    }

    public List<Map<String, Object>> findAll() {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT id, name, language, duration, type, created_by, file_path, size_bytes, updated_at FROM voice_prompts ORDER BY created_at DESC";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> map = new HashMap<>();
                map.put("name", rs.getString("name"));
                map.put("language", rs.getString("language"));
                map.put("duration", rs.getString("duration"));
                map.put("type", rs.getString("type"));
                map.put("createdBy", rs.getString("created_by"));
                map.put("filePath", rs.getString("file_path"));
                map.put("sizeBytes", rs.getLong("size_bytes"));
                map.put("updatedAt", rs.getTimestamp("updated_at"));
                list.add(map);
            }
        } catch (SQLException e) {
            System.err.println("Error finding all voice prompts: " + e.getMessage());
        }
        return list;
    }

    public boolean deleteByName(String name) {
        String sql = "DELETE FROM voice_prompts WHERE name = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, name);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error deleting voice prompt by name: " + e.getMessage());
            return false;
        }
    }
}
