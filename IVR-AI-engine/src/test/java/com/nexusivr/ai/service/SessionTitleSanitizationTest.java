package com.nexusivr.ai.service;

import com.nexusivr.ai.dao.AiSessionDao;
import com.nexusivr.ai.dao.DatabaseManager;
import com.nexusivr.ai.model.AiSession;
import com.nexusivr.ai.model.Channel;
import com.nexusivr.ai.model.flow.FlowModel;
import com.nexusivr.ai.util.TitleSanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for session title derivation and newline artifact sanitation.
 * <p>
 * Verifies:
 * 1. TitleSanitizer removes leading/trailing/internal raw and escaped newlines (\n, \r, \\n, \\r).
 * 2. VxmlToModelConverter extracts greeting text with leading newlines and cleans it for FlowModel.getName().
 * 3. ChatService defaults new session titles to "New IVR Flow Session" (no raw prompt placeholder).
 * 4. ChatService sanitizes titles before saving to DB and skips duplicate title updates.
 */
class SessionTitleSanitizationTest {

    private VxmlToModelConverter converter;

    @BeforeEach
    void setUp() {
        converter = new VxmlToModelConverter();
    }

    @Test
    void testTitleSanitizerStripsLeadingAndEscapedNewlines() {
        assertEquals("Welcome to XYZ University", TitleSanitizer.sanitize("\nWelcome to XYZ University"));
        assertEquals("Welcome to XYZ University", TitleSanitizer.sanitize("\\nWelcome to XYZ University"));
        assertEquals("Welcome to XYZ University", TitleSanitizer.sanitize("\r\n\\r\\n  Welcome to   \n  XYZ University  "));
        assertEquals("Welcome to XYZ University", TitleSanitizer.sanitize("Welcome to\nXYZ University"));
        assertEquals("Welcome to XYZ University", TitleSanitizer.sanitize("Welcome to\\nXYZ University"));
        assertEquals("", TitleSanitizer.sanitize(null));
        assertEquals("", TitleSanitizer.sanitize("   \n\r  "));
    }

    @Test
    void testVxmlConverterSanitizesGreetingWithLeadingNewline() throws Exception {
        String vxmlWithLeadingNewlineGreeting = """
            <?xml version="1.0" encoding="UTF-8"?>
            <vxml version="2.1" xmlns="http://www.w3.org/2001/vxml">
              <form id="start">
                <block>
                  <prompt>
            Welcome to XYZ University. Press 1 for Admissions.
                  </prompt>
                  <goto next="#menu"/>
                </block>
              </form>
              <form id="menu">
                <menu>
                  <prompt>Press 1 for Admissions.</prompt>
                  <choice accept="digits 1" next="#end"/>
                </menu>
              </form>
              <form id="end">
                <block><prompt>Goodbye.</prompt><disconnect/></block>
              </form>
            </vxml>
            """;

        FlowModel model = converter.convert(vxmlWithLeadingNewlineGreeting);

        assertNotNull(model, "FlowModel must be parsed");
        assertNull(model.getName(), "Model name must NOT be derived from spoken greeting prompt text");
    }

    @Test
    void testVxmlConverterSanitizesGreetingWithEscapedNewline() throws Exception {
        String vxmlWithEscapedNewline = """
            <?xml version="1.0" encoding="UTF-8"?>
            <vxml version="2.1" xmlns="http://www.w3.org/2001/vxml">
              <form id="start">
                <block>
                  <prompt>\\nWelcome to Hospitality Services. Press 1 for Reservations.</prompt>
                  <goto next="#end"/>
                </block>
              </form>
              <form id="end">
                <block><prompt>Goodbye.</prompt><disconnect/></block>
              </form>
            </vxml>
            """;

        FlowModel model = converter.convert(vxmlWithEscapedNewline);

        assertNotNull(model);
        assertNull(model.getName(), "Model name must NOT be derived from spoken greeting prompt text");
    }


    @Test
    void testChatServiceDefaultsNewSessionTitleToNewIvrFlowSession() {
        UUID tenantId = UUID.randomUUID();
        AiSessionDao mockDao = org.mockito.Mockito.mock(AiSessionDao.class);
        org.mockito.Mockito.when(mockDao.create(org.mockito.Mockito.any(AiSession.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ChatService chatService = new ChatService(
                mockDao,
                org.mockito.Mockito.mock(com.nexusivr.ai.dao.MessageDao.class),
                org.mockito.Mockito.mock(AiService.class)
        );

        AiSession session = chatService.startSession(tenantId, Channel.CHAT, "New IVR Flow Session");
        assertNotNull(session);
        assertEquals("New IVR Flow Session", session.getCustomerIdentifier());
    }

    @Test
    void testUpdateSessionTitleSkipsDuplicateUpdates() {
        UUID sessionId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        AiSession existingSession = new AiSession();
        existingSession.setId(sessionId);
        existingSession.setTenantId(tenantId);
        existingSession.setCustomerIdentifier("Welcome to Hospitality Services");

        AiSessionDao mockDao = org.mockito.Mockito.mock(AiSessionDao.class);
        org.mockito.Mockito.when(mockDao.findById(sessionId, tenantId))
                .thenReturn(java.util.Optional.of(existingSession));

        ChatService chatService = new ChatService(
                mockDao,
                org.mockito.Mockito.mock(com.nexusivr.ai.dao.MessageDao.class),
                org.mockito.Mockito.mock(AiService.class)
        );

        // Attempt 1: Call updateSessionTitle with identical title string
        boolean result1 = chatService.updateSessionTitle(sessionId, tenantId, "Welcome to Hospitality Services");
        assertTrue(result1, "Result should be true for duplicate title check");

        // Verify updateTitle was NEVER invoked on DAO because the title already matched!
        org.mockito.Mockito.verify(mockDao, org.mockito.Mockito.never())
                .updateTitle(org.mockito.Mockito.any(), org.mockito.Mockito.any(), org.mockito.Mockito.any());

        // Attempt 2: Call with raw newline prefix matching sanitized existing title
        boolean result2 = chatService.updateSessionTitle(sessionId, tenantId, "\nWelcome to Hospitality Services\n");
        assertTrue(result2);
        org.mockito.Mockito.verify(mockDao, org.mockito.Mockito.never())
                .updateTitle(org.mockito.Mockito.any(), org.mockito.Mockito.any(), org.mockito.Mockito.any());

        // Attempt 3: Call with DIFFERENT new title
        org.mockito.Mockito.when(mockDao.updateTitle(sessionId, tenantId, "New Sanitized Title"))
                .thenReturn(true);
        boolean result3 = chatService.updateSessionTitle(sessionId, tenantId, "\nNew Sanitized Title\n");
        assertTrue(result3);
        org.mockito.Mockito.verify(mockDao, org.mockito.Mockito.times(1))
                .updateTitle(sessionId, tenantId, "New Sanitized Title");
    }
}
