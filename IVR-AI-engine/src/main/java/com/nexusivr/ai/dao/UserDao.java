package com.nexusivr.ai.dao;

import com.nexusivr.ai.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class UserDao {

    private static final Logger logger = LoggerFactory.getLogger(UserDao.class);

    private static final String FIND_BY_EMAIL_OR_USERNAME_SQL = """
        SELECT id, active_tenant_id, email, password, is_superadmin, username, status, last_login_at, created_at, updated_at
        FROM users
        WHERE LOWER(email) = LOWER(?) OR LOWER(username) = LOWER(?)
        """;

    private static final String FIND_BY_ID_SQL = """
        SELECT id, active_tenant_id, email, password, is_superadmin, username, status, last_login_at, created_at, updated_at
        FROM users
        WHERE id = ?::uuid
        """;

    public List<User> findAllUsers() {
        List<User> list = new ArrayList<>();
        String sql = """
            SELECT id, active_tenant_id, email, password, is_superadmin, username, status, last_login_at, created_at, updated_at
            FROM users
            ORDER BY created_at DESC
            """;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapUser(rs));
            }
        } catch (SQLException e) {
            logger.error("Error listing users: {}", e.getMessage(), e);
        }
        return list;
    }

    public User createUser(User user) {
        String sql = """
            INSERT INTO users (id, active_tenant_id, email, password, is_superadmin, username, status, created_at, updated_at)
            VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            RETURNING id, active_tenant_id, email, password, is_superadmin, username, status, last_login_at, created_at, updated_at
            """;
        String newId = user.getId() != null ? user.getId() : UUID.randomUUID().toString();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newId);
            if (user.getActiveTenantId() != null && !user.getActiveTenantId().isBlank()) {
                ps.setString(2, user.getActiveTenantId());
            } else {
                ps.setNull(2, java.sql.Types.OTHER);
            }
            ps.setString(3, user.getEmail());
            ps.setString(4, user.getPasswordHash() != null ? user.getPasswordHash() : "password");
            ps.setBoolean(5, user.isSuperadmin());
            ps.setString(6, user.getUsername());
            ps.setString(7, user.getStatus() != null ? user.getStatus() : "ACTIVE");

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapUser(rs);
                }
            }
        } catch (SQLException e) {
            logger.error("Error creating user: {}", e.getMessage(), e);
        }
        return null;
    }

    public boolean updateUser(User user) {
        String sql = """
            UPDATE users
            SET email = ?, username = ?, is_superadmin = ?, status = ?, active_tenant_id = ?::uuid, updated_at = CURRENT_TIMESTAMP
            WHERE id = ?::uuid
            """;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getEmail());
            ps.setString(2, user.getUsername());
            ps.setBoolean(3, user.isSuperadmin());
            ps.setString(4, user.getStatus());
            if (user.getActiveTenantId() != null && !user.getActiveTenantId().isBlank()) {
                ps.setString(5, user.getActiveTenantId());
            } else {
                ps.setNull(5, java.sql.Types.OTHER);
            }
            ps.setString(6, user.getId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Error updating user {}: {}", user.getId(), e.getMessage(), e);
            return false;
        }
    }

    public void updateLastLogin(String userId) {
        String sql = """
            UPDATE users
            SET last_login_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
            WHERE id = ?::uuid
            """;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error updating last login timestamp for user {}: {}", userId, e.getMessage(), e);
        }
    }

    public boolean updatePassword(String userId, String newPassword) {
        String sql = """
            UPDATE users
            SET password = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ?::uuid
            """;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newPassword);
            ps.setString(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Error updating password for user {}: {}", userId, e.getMessage(), e);
            return false;
        }
    }

    public boolean deleteUser(String userId) {
        String sql = "DELETE FROM users WHERE id = ?::uuid";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Error deleting user {}: {}", userId, e.getMessage(), e);
            return false;
        }
    }

    public User findByEmailOrUsername(String identifier) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_BY_EMAIL_OR_USERNAME_SQL)) {
            ps.setString(1, identifier.trim());
            ps.setString(2, identifier.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapUser(rs);
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding user by email/username: {}", identifier, e);
        }
        return null;
    }

    public User findById(String userId) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_BY_ID_SQL)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapUser(rs);
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding user by id: {}", userId, e);
        }
        return null;
    }

    private User mapUser(ResultSet rs) throws SQLException {
        User u = new User();
        u.setId(rs.getString("id"));
        u.setActiveTenantId(rs.getString("active_tenant_id"));
        u.setEmail(rs.getString("email"));
        u.setPasswordHash(rs.getString("password"));
        u.setSuperadmin(rs.getBoolean("is_superadmin"));
        u.setUsername(rs.getString("username"));
        u.setStatus(rs.getString("status"));
        u.setLastLoginAt(rs.getTimestamp("last_login_at"));
        u.setCreatedAt(rs.getTimestamp("created_at"));
        u.setUpdatedAt(rs.getTimestamp("updated_at"));
        return u;
    }
}
