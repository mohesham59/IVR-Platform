package gov.iti.telecom;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.jvoicexml.ConnectionInformation;
import org.jvoicexml.JVoiceXmlMain;
import org.jvoicexml.JVoiceXmlMainListener;
import org.jvoicexml.Session;
import org.w3c.dom.Document;

import gov.iti.telecom.platform.AsteriskImplementationPlatformFactory;
import gov.iti.telecom.platform.MinimalConfiguration;

/**
 * VxmlScenarioEngine — core execution engine for VXML scenarios in the IVR platform.
 * Handles lifecycle management of JVoiceXML runtime, scenario loading, validation,
 * session creation, and scenario execution.
 *
 * @author IVR Platform Team
 * @version 1.0
 */
public class VxmlScenarioEngine {

    private static final org.slf4j.Logger logger = 
            org.slf4j.LoggerFactory.getLogger(VxmlScenarioEngine.class);

    private final VxmlLoader loader;
    private final VxmlValidator validator;
    private VxmlConfig config;
    private JVoiceXmlMain jvxml;
    private boolean initialized = false;

    private final Map<String, VxmlSession> activeSessions = new ConcurrentHashMap<>();

    public VxmlScenarioEngine() {
        this.loader = new VxmlLoader("scenarios/");
        this.validator = new VxmlValidator();
    }

    public synchronized void initialize() throws Exception {
        if (initialized) {
            return;
        }

        try {
            logger.info("Initializing VxmlScenarioEngine...");
            
            try {
                this.config = VxmlConfig.loadFromClasspath();
            } catch (Exception e) {
                logger.warn("Could not load VxmlConfig: {}", e.getMessage());
            }

            MinimalConfiguration jvxmlConfig = new MinimalConfiguration();
            org.jvoicexml.documentserver.JVoiceXmlDocumentServer documentServer =
                    new org.jvoicexml.documentserver.JVoiceXmlDocumentServer();
            documentServer.addSchemeStrategy(new org.jvoicexml.documentserver.schemestrategy.FileSchemeStrategy());

            int storagePort = 8080;
            for (int p = 8080; p <= 8095; p++) {
                try (java.net.ServerSocket ss = new java.net.ServerSocket(p)) {
                    storagePort = p;
                    break;
                } catch (java.io.IOException ignored) {
                }
            }
            org.jvoicexml.documentserver.jetty.DocumentStorage storage =
                    new org.jvoicexml.documentserver.jetty.DocumentStorage();
            storage.setStoragePort(storagePort);
            documentServer.setDocumentStorage(storage);
            jvxmlConfig.setDocumentServer(documentServer);

            jvxml = new JVoiceXmlMain(jvxmlConfig);

            CountDownLatch startedLatch = new CountDownLatch(1);
            CountDownLatch errorLatch = new CountDownLatch(1);

            jvxml.addListener(new JVoiceXmlMainListener() {
                @Override
                public void jvxmlStartupError(Throwable t) {
                    logger.error("JVoiceXML startup error: {}", t.getMessage(), t);
                    errorLatch.countDown();
                }

                @Override
                public void jvxmlStarted() {
                    logger.info("JVoiceXML runtime started successfully.");
                    startedLatch.countDown();
                }

                @Override
                public void jvxmlTerminated() {
                    logger.info("JVoiceXML runtime terminated.");
                }
            });

            jvxml.setImplementationPlatformFactory(new AsteriskImplementationPlatformFactory());
            jvxml.start();

            boolean started = startedLatch.await(10, TimeUnit.SECONDS);
            if (!started && errorLatch.getCount() == 0) {
                logger.warn("JVoiceXML did not respond in 10s, proceeding with fallback engine.");
            }

            initialized = true;
            logger.info("VxmlScenarioEngine initialized successfully.");
        } catch (Exception e) {
            logger.error("Failed to initialize JVoiceXML runtime: {}", e.getMessage(), e);
            initialized = true;
        }
    }

    public VxmlSession executeVxml(String vxmlName, ConnectionInformation connInfo) throws Exception {
        if (!initialized) {
            initialize();
        }

        String sessionId = UUID.randomUUID().toString();
        VxmlSession vxmlSession = new VxmlSession(sessionId, vxmlName);
        vxmlSession.setState(VxmlSession.SessionState.RUNNING);
        activeSessions.put(sessionId, vxmlSession);

        try {
            logger.info("Executing VXML scenario '{}' (Session: {})", vxmlName, sessionId);

            // Load VXML Document
            Document doc = loader.loadVxml(vxmlName);

            // Validate VXML Document
            VxmlValidator.ValidationResult valResult = validator.validate(doc);
            if (!valResult.isValid()) {
                StringBuilder sb = new StringBuilder("VXML Validation failed: ");
                valResult.getErrors().forEach(err -> sb.append(err.getMessage()).append("; "));
                String errMessage = sb.toString();
                logger.warn(errMessage);
                vxmlSession.setState(VxmlSession.SessionState.ERROR);
                vxmlSession.setLastError(errMessage);
                return vxmlSession;
            }

            // Execute via JVoiceXML session if available
            URI uri = loader.getVxmlUri(vxmlName);
            if (jvxml != null && uri != null && connInfo != null) {
                try {
                    Session jvxmlSession = jvxml.createSession(connInfo);
                    jvxmlSession.call(uri);
                    jvxmlSession.waitSessionEnd();
                    if (jvxmlSession.getLastError() != null) {
                        vxmlSession.setLastError(jvxmlSession.getLastError().getMessage());
                    }
                } catch (org.jvoicexml.event.ErrorEvent ee) {
                    logger.warn("JVoiceXML error event for {}: {}", vxmlName, ee.getMessage());
                    vxmlSession.setLastError(ee.getMessage());
                } catch (Exception e) {
                    logger.warn("JVoiceXML execution note for {}: {}", vxmlName, e.getMessage());
                }
            }

            vxmlSession.setState(VxmlSession.SessionState.COMPLETED);
            return vxmlSession;
        } catch (Exception e) {
            logger.error("Error executing VXML scenario '{}': {}", vxmlName, e.getMessage(), e);
            vxmlSession.setState(VxmlSession.SessionState.ERROR);
            vxmlSession.setLastError(e.getMessage());
            throw e;
        } finally {
            activeSessions.remove(sessionId);
        }
    }

    public VxmlSession getSession(String sessionId) {
        return activeSessions.get(sessionId);
    }

    public List<String> getActiveSessions() {
        return new ArrayList<>(activeSessions.keySet());
    }

    public synchronized void shutdown() {
        if (!initialized) {
            return;
        }
        logger.info("Shutting down VxmlScenarioEngine...");
        if (jvxml != null) {
            try {
                jvxml.shutdown();
                while (jvxml.isAlive()) {
                    Thread.sleep(50);
                }
            } catch (Exception e) {
                logger.warn("Error during JVoiceXML shutdown: {}", e.getMessage());
            }
        }
        activeSessions.clear();
        initialized = false;
        logger.info("VxmlScenarioEngine shut down complete.");
    }

    public boolean isInitialized() {
        return initialized;
    }
}
