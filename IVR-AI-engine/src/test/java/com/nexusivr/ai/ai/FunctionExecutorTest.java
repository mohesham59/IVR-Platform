package com.nexusivr.ai.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FunctionExecutor Unit Tests")
public class FunctionExecutorTest {

    private FunctionExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new FunctionExecutor();
    }

    @Test
    @DisplayName("Should execute transferToAgent function")
    void testTransferToAgent() {
        String json = executor.executeFunction("transferToAgent", Map.of("department", "billing", "priority", "high"));
        assertNotNull(json);
        assertTrue(json.contains("transferToAgent"));
        assertTrue(json.contains("billing"));
        assertTrue(json.contains("high"));
        assertTrue(json.contains("SUCCESS"));
    }

    @Test
    @DisplayName("Should execute checkCustomerBalance function")
    void testCheckCustomerBalance() {
        String json = executor.executeFunction("checkCustomerBalance", Map.of("customerId", "CUST-999"));
        assertNotNull(json);
        assertTrue(json.contains("checkCustomerBalance"));
        assertTrue(json.contains("CUST-999"));
        assertTrue(json.contains("250.75"));
        assertTrue(json.contains("USD"));
    }

    @Test
    @DisplayName("Should execute createSupportTicket function")
    void testCreateSupportTicket() {
        String json = executor.executeFunction("createSupportTicket", Map.of("customerId", "CUST-999", "issue", "Internet down"));
        assertNotNull(json);
        assertTrue(json.contains("createSupportTicket"));
        assertTrue(json.contains("TICK-"));
        assertTrue(json.contains("Internet down"));
    }

    @Test
    @DisplayName("Should execute bookAppointment function")
    void testBookAppointment() {
        String json = executor.executeFunction("bookAppointment", Map.of("date", "2026-09-01", "timeSlot", "02:00 PM"));
        assertNotNull(json);
        assertTrue(json.contains("bookAppointment"));
        assertTrue(json.contains("APT-"));
        assertTrue(json.contains("2026-09-01"));
    }

    @Test
    @DisplayName("Should throw AiException when executing an unregistered function")
    void testUnregisteredFunctionExecution() {
        AiException exception = assertThrows(AiException.class, () -> 
                executor.executeFunction("unknownFunction", Map.of())
        );
        assertTrue(exception.getMessage().contains("Unregistered function call"));
    }
}
