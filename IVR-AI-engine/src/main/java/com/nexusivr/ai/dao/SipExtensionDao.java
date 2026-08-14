package com.nexusivr.ai.dao;

import com.nexusivr.ai.model.SipExtension;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SipExtensionDao {

    public SipExtensionDao() {}

    public List<SipExtension> findByTenantId(UUID tenantId) {
        List<SipExtension> list = new ArrayList<>();
        String sql = "SELECT id, tenant_id, extension_number, display_name, assigned_user_id, sip_password, tls_enabled, created_at, updated_at " +
                     "FROM sip_extensions WHERE tenant_id = ? ORDER BY extension_number ASC";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setObject(1, tenantId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching SIP extensions for tenant " + tenantId + ": " + e.getMessage());
        }
        return list;
    }

    public SipExtension findById(UUID tenantId, UUID id) {
        String sql = "SELECT id, tenant_id, extension_number, display_name, assigned_user_id, sip_password, tls_enabled, created_at, updated_at " +
                     "FROM sip_extensions WHERE tenant_id = ? AND id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setObject(1, tenantId);
            pstmt.setObject(2, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching SIP extension by ID " + id + ": " + e.getMessage());
        }
        return null;
    }

    public boolean existsByExtension(UUID tenantId, String extensionNumber) {
        String sql = "SELECT 1 FROM sip_extensions WHERE tenant_id = ? AND extension_number = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setObject(1, tenantId);
            pstmt.setString(2, extensionNumber);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("Error checking extension existence: " + e.getMessage());
            return false;
        }
    }

    public SipExtension save(SipExtension ext) {
        String sql = "INSERT INTO sip_extensions (tenant_id, extension_number, display_name, assigned_user_id, sip_password, tls_enabled) " +
                     "VALUES (?, ?, ?, ?, ?, ?) RETURNING id, tenant_id, extension_number, display_name, assigned_user_id, sip_password, tls_enabled, created_at, updated_at";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setObject(1, ext.getTenantId());
            pstmt.setString(2, ext.getExtensionNumber());
            pstmt.setString(3, ext.getDisplayName());
            pstmt.setObject(4, ext.getAssignedUserId());
            pstmt.setString(5, ext.getSipPassword());
            pstmt.setBoolean(6, ext.isTlsEnabled());

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error saving SIP extension: " + e.getMessage());
            throw new RuntimeException("Database error saving SIP extension: " + e.getMessage(), e);
        }
        return null;
    }

    public boolean delete(UUID tenantId, UUID id) {
        String sql = "DELETE FROM sip_extensions WHERE tenant_id = ? AND id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setObject(1, tenantId);
            pstmt.setObject(2, id);
            int rows = pstmt.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            System.err.println("Error deleting SIP extension: " + e.getMessage());
            return false;
        }
    }

    private SipExtension mapResultSet(ResultSet rs) throws SQLException {
        SipExtension e = new SipExtension();
        e.setId((UUID) rs.getObject("id"));
        e.setTenantId((UUID) rs.getObject("tenant_id"));
        e.setExtensionNumber(rs.getString("extension_number"));
        e.setDisplayName(rs.getString("display_name"));
        e.setAssignedUserId((UUID) rs.getObject("assigned_user_id"));
        e.setSipPassword(rs.getString("sip_password"));
        e.setTlsEnabled(rs.getBoolean("tls_enabled"));
        e.setCreatedAt(rs.getTimestamp("created_at"));
        e.setUpdatedAt(rs.getTimestamp("updated_at"));
        return e;
    }
}
