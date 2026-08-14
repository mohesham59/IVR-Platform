package com.nexusivr.ai.dao;

import com.nexusivr.ai.model.PhoneNumber;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PhoneNumberDao {

    public PhoneNumberDao() {}

    public List<PhoneNumber> findByTenantId(UUID tenantId) {
        List<PhoneNumber> numbers = new ArrayList<>();
        String sql = "SELECT id, tenant_id, phone_number, country, provider, assigned_flow_id, assigned_flow_name, status, created_at, updated_at " +
                     "FROM phone_numbers WHERE tenant_id = ? ORDER BY created_at ASC";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setObject(1, tenantId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    numbers.add(mapResultSetToPhoneNumber(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching phone numbers for tenant " + tenantId + ": " + e.getMessage());
        }
        return numbers;
    }

    public PhoneNumber findById(UUID tenantId, UUID id) {
        String sql = "SELECT id, tenant_id, phone_number, country, provider, assigned_flow_id, assigned_flow_name, status, created_at, updated_at " +
                     "FROM phone_numbers WHERE tenant_id = ? AND id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setObject(1, tenantId);
            pstmt.setObject(2, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToPhoneNumber(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching phone number by ID " + id + ": " + e.getMessage());
        }
        return null;
    }

    public PhoneNumber save(PhoneNumber number) {
        String sql = "INSERT INTO phone_numbers (tenant_id, phone_number, country, provider, assigned_flow_id, assigned_flow_name, status) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id, tenant_id, phone_number, country, provider, assigned_flow_id, assigned_flow_name, status, created_at, updated_at";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setObject(1, number.getTenantId());
            pstmt.setString(2, number.getPhoneNumber());
            pstmt.setString(3, number.getCountry() != null ? number.getCountry() : "US");
            pstmt.setString(4, number.getProvider() != null ? number.getProvider() : "Twilio");
            pstmt.setString(5, number.getAssignedFlowId());
            pstmt.setString(6, number.getAssignedFlowName());
            pstmt.setString(7, number.getStatus() != null ? number.getStatus() : "UNASSIGNED");

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToPhoneNumber(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error saving phone number: " + e.getMessage());
            throw new RuntimeException("Database error saving phone number: " + e.getMessage(), e);
        }
        return null;
    }

    public boolean updateAssignment(UUID tenantId, UUID id, String flowId, String flowName, String status) {
        String sql = "UPDATE phone_numbers SET assigned_flow_id = ?, assigned_flow_name = ?, status = ?, updated_at = now() " +
                     "WHERE tenant_id = ? AND id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, flowId);
            pstmt.setString(2, flowName);
            pstmt.setString(3, status);
            pstmt.setObject(4, tenantId);
            pstmt.setObject(5, id);

            int rows = pstmt.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            System.err.println("Error updating phone number assignment: " + e.getMessage());
            throw new RuntimeException("Database error updating assignment: " + e.getMessage(), e);
        }
    }

    public boolean delete(UUID tenantId, UUID id) {
        String sql = "DELETE FROM phone_numbers WHERE tenant_id = ? AND id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setObject(1, tenantId);
            pstmt.setObject(2, id);

            int rows = pstmt.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            System.err.println("Error deleting phone number: " + e.getMessage());
            return false;
        }
    }

    private PhoneNumber mapResultSetToPhoneNumber(ResultSet rs) throws SQLException {
        PhoneNumber p = new PhoneNumber();
        p.setId((UUID) rs.getObject("id"));
        p.setTenantId((UUID) rs.getObject("tenant_id"));
        p.setPhoneNumber(rs.getString("phone_number"));
        p.setCountry(rs.getString("country"));
        p.setProvider(rs.getString("provider"));
        p.setAssignedFlowId(rs.getString("assigned_flow_id"));
        p.setAssignedFlowName(rs.getString("assigned_flow_name"));
        p.setStatus(rs.getString("status"));
        p.setCreatedAt(rs.getTimestamp("created_at"));
        p.setUpdatedAt(rs.getTimestamp("updated_at"));
        return p;
    }

    public int getTodaysInboundCallsCount(UUID tenantId) {
        String sql = "SELECT COUNT(*) FROM call_logs WHERE tenant_id = ? AND start_time >= CURRENT_DATE AND (caller_id LIKE '+%' OR caller_id IN (SELECT phone_number FROM phone_numbers WHERE tenant_id = ?))";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setObject(1, tenantId);
            pstmt.setObject(2, tenantId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error counting today's inbound calls for tenant " + tenantId + ": " + e.getMessage());
        }
        return 0;
    }
}
