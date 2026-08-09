package com.nexusivr.payment.dao;

import com.nexusivr.payment.model.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JDBC Data Access Object for transactions table.
 */
public class TransactionDao {

    private static final Logger logger = LoggerFactory.getLogger(TransactionDao.class);

    public Transaction createTransaction(Transaction txn) throws SQLException {
        if (txn.getId() == null) {
            txn.setId(UUID.randomUUID());
        }

        Timestamp now = new Timestamp(System.currentTimeMillis());
        txn.setCreatedAt(now);
        txn.setUpdatedAt(now);

        String sql = "INSERT INTO transactions (id, tenant_id, type, amount_piasters, currency, status, " +
                     "paymob_transaction_id, paymob_order_id, plan_id, card_token, created_at, updated_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, txn.getId());
            stmt.setObject(2, txn.getTenantId());
            stmt.setString(3, txn.getType());
            stmt.setLong(4, txn.getAmountPiasters());
            stmt.setString(5, txn.getCurrency() != null ? txn.getCurrency() : "EGP");
            stmt.setString(6, txn.getStatus());
            stmt.setString(7, txn.getPaymobTransactionId());
            stmt.setString(8, txn.getPaymobOrderId());
            stmt.setObject(9, txn.getPlanId());
            stmt.setString(10, txn.getCardToken());
            stmt.setTimestamp(11, now);
            stmt.setTimestamp(12, now);

            stmt.executeUpdate();
            logger.info("Recorded payment transaction: ID={}, Tenant={}, Amount={} piasters, Status={}", 
                    txn.getId(), txn.getTenantId(), txn.getAmountPiasters(), txn.getStatus());
            return txn;
        }
    }

    public Transaction findTransactionById(UUID id) throws SQLException {
        String sql = "SELECT id, tenant_id, type, amount_piasters, currency, status, " +
                     "paymob_transaction_id, paymob_order_id, plan_id, card_token, created_at, updated_at " +
                     "FROM transactions WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRowToTransaction(rs);
                }
            }
        }
        return null;
    }

    public List<Transaction> findTransactionsByTenantId(UUID tenantId) throws SQLException {
        List<Transaction> list = new ArrayList<>();
        String sql = "SELECT id, tenant_id, type, amount_piasters, currency, status, " +
                     "paymob_transaction_id, paymob_order_id, plan_id, card_token, created_at, updated_at " +
                     "FROM transactions WHERE tenant_id = ? ORDER BY created_at DESC";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, tenantId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToTransaction(rs));
                }
            }
        }
        return list;
    }
    /** Retrieve all transactions across all tenants, with company name from tenants table. Used by superadmin view. */
    public List<Transaction> findAllTransactions() throws SQLException {
        List<Transaction> list = new ArrayList<>();
        String sql = "SELECT t.id, t.tenant_id, t.type, t.amount_piasters, t.currency, t.status, " +
                     "t.paymob_transaction_id, t.paymob_order_id, t.plan_id, t.card_token, t.created_at, t.updated_at, " +
                     "tn.display_name AS tenant_display_name " +
                     "FROM transactions t " +
                     "LEFT JOIN tenants tn ON t.tenant_id = tn.id " +
                     "ORDER BY t.created_at DESC";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Transaction txn = mapRowToTransaction(rs);
                    txn.setTenantDisplayName(rs.getString("tenant_display_name"));
                    list.add(txn);
                }
            }
        }
        return list;
    }

    public boolean updateTransactionStatus(UUID id, String status, String paymobTransactionId, String paymobOrderId) throws SQLException {
        String sql = "UPDATE transactions SET status = ?, paymob_transaction_id = COALESCE(?, paymob_transaction_id), " +
                     "paymob_order_id = COALESCE(?, paymob_order_id), updated_at = now() WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, status);
            stmt.setString(2, paymobTransactionId);
            stmt.setString(3, paymobOrderId);
            stmt.setObject(4, id);

            int updated = stmt.executeUpdate();
            return updated > 0;
        }
    }

    /**
     * Bulk-updates all PENDING transactions older than {@code olderThanMinutes} minutes to EXPIRED.
     * @return the number of rows updated
     */
    public int expireStalePendingTransactions(int olderThanMinutes) throws SQLException {
        String sql = "UPDATE transactions SET status = 'EXPIRED', updated_at = now() " +
                     "WHERE status = 'PENDING' AND created_at < now() - (? || ' minutes')::interval";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, String.valueOf(olderThanMinutes));
            int count = stmt.executeUpdate();
            if (count > 0) {
                logger.info("Expired {} stale PENDING transaction(s) older than {} minutes.", count, olderThanMinutes);
            }
            return count;
        }
    }

    /**
     * Cancels a specific PENDING transaction, verifying it belongs to the given tenant.
     * Only transitions PENDING → CANCELLED; already-settled transactions are not changed.
     * @return true if the row was updated, false if not found or not PENDING or wrong tenant
     */
    public boolean cancelTransaction(UUID transactionId, UUID tenantId) throws SQLException {
        String sql = "UPDATE transactions SET status = 'CANCELLED', updated_at = now() " +
                     "WHERE id = ? AND tenant_id = ? AND status = 'PENDING'";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, transactionId);
            stmt.setObject(2, tenantId);
            int updated = stmt.executeUpdate();
            if (updated > 0) {
                logger.info("Cancelled PENDING transaction {} for tenant {}", transactionId, tenantId);
            }
            return updated > 0;
        }
    }

    private Transaction mapRowToTransaction(ResultSet rs) throws SQLException {
        Transaction txn = new Transaction();
        txn.setId((UUID) rs.getObject("id"));
        txn.setTenantId((UUID) rs.getObject("tenant_id"));
        txn.setType(rs.getString("type"));
        txn.setAmountPiasters(rs.getLong("amount_piasters"));
        txn.setCurrency(rs.getString("currency"));
        txn.setStatus(rs.getString("status"));
        txn.setPaymobTransactionId(rs.getString("paymob_transaction_id"));
        txn.setPaymobOrderId(rs.getString("paymob_order_id"));
        txn.setPlanId((UUID) rs.getObject("plan_id"));
        txn.setCardToken(rs.getString("card_token"));
        txn.setCreatedAt(rs.getTimestamp("created_at"));
        txn.setUpdatedAt(rs.getTimestamp("updated_at"));
        return txn;
    }
}
