package com.nexusivr.payment.dao;

import com.nexusivr.payment.config.AppServletContextListener;
import com.nexusivr.payment.model.SubscriptionPlan;
import com.nexusivr.payment.model.Transaction;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseMigrationAndDaoTest {

    private SubscriptionPlanDao subscriptionPlanDao;
    private TransactionDao transactionDao;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:testdb;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");

        DatabaseManager.setDataSourceForTesting(ds);

        // Pre-create tenants table for H2 test environment
        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement()) {
            try {
                stmt.execute("CREATE ALIAS gen_random_uuid FOR \"java.util.UUID.randomUUID\"");
            } catch (Exception ignored) {
            }
            stmt.execute("CREATE TABLE IF NOT EXISTS tenants (" +
                         "id UUID DEFAULT RANDOM_UUID() PRIMARY KEY, " +
                         "display_name VARCHAR(255), " +
                         "status VARCHAR(20) DEFAULT 'ACTIVE', " +
                         "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                         "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        }

        subscriptionPlanDao = new SubscriptionPlanDao();
        transactionDao = new TransactionDao();
    }

    @AfterEach
    void tearDown() {
        DatabaseManager.shutdown();
    }

    @Test
    void testMigrationRunsCleanlyAndIdempotently() throws Exception {
        AppServletContextListener listener = new AppServletContextListener();

        // 1. Run migration first time
        assertDoesNotThrow(() -> listener.runDbMigration());

        List<SubscriptionPlan> plansFirstRun = subscriptionPlanDao.findAllPlans();
        assertNotNull(plansFirstRun);
        assertEquals(3, plansFirstRun.size(), "Should seed 3 default subscription plans");

        assertTrue(plansFirstRun.stream().anyMatch(p -> "Starter".equals(p.getName()) && p.getPricePiasters() == 50000L));
        assertTrue(plansFirstRun.stream().anyMatch(p -> "Business".equals(p.getName()) && p.getPricePiasters() == 150000L));
        assertTrue(plansFirstRun.stream().anyMatch(p -> "Enterprise".equals(p.getName()) && p.getPricePiasters() == 500000L));

        // 2. Run migration second time (Idempotency Check)
        assertDoesNotThrow(() -> listener.runDbMigration());

        List<SubscriptionPlan> plansSecondRun = subscriptionPlanDao.findAllPlans();
        assertEquals(3, plansSecondRun.size(), "Second migration run should not duplicate seed data");
    }

    @Test
    void testTransactionDaoCrudOperations() throws Exception {
        AppServletContextListener listener = new AppServletContextListener();
        listener.runDbMigration();

        UUID tenantId = UUID.randomUUID();
        // Insert a dummy tenant row into tenants table
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO tenants (id, display_name) VALUES ('" + tenantId + "', 'Test Enterprise Tenant')");
        }

        // 1. Create Transaction
        Transaction txn = new Transaction();
        txn.setTenantId(tenantId);
        txn.setType("SUBSCRIPTION");
        txn.setAmountPiasters(150000L); // 1,500.00 EGP
        txn.setCurrency("EGP");
        txn.setStatus("PENDING");
        txn.setPaymobOrderId("ORD_991823");
        txn.setCardToken("TOKEN_XYZ_123");

        Transaction created = transactionDao.createTransaction(txn);
        assertNotNull(created.getId());
        assertNotNull(created.getCreatedAt());

        // 2. Read Transaction back by ID
        Transaction fetched = transactionDao.findTransactionById(created.getId());
        assertNotNull(fetched);
        assertEquals(tenantId, fetched.getTenantId());
        assertEquals("SUBSCRIPTION", fetched.getType());
        assertEquals(150000L, fetched.getAmountPiasters());
        assertEquals("EGP", fetched.getCurrency());
        assertEquals("PENDING", fetched.getStatus());
        assertEquals("ORD_991823", fetched.getPaymobOrderId());

        // 3. Find by Tenant ID
        List<Transaction> tenantTxns = transactionDao.findTransactionsByTenantId(tenantId);
        assertEquals(1, tenantTxns.size());
        assertEquals(created.getId(), tenantTxns.get(0).getId());

        // 4. Update Transaction Status
        boolean updated = transactionDao.updateTransactionStatus(created.getId(), "SUCCESS", "PAYMOB_TXN_88210", "ORD_991823");
        assertTrue(updated);

        Transaction updatedFetched = transactionDao.findTransactionById(created.getId());
        assertEquals("SUCCESS", updatedFetched.getStatus());
        assertEquals("PAYMOB_TXN_88210", updatedFetched.getPaymobTransactionId());
    }
}
