package gov.iti.telecom;

import java.io.File;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.jvoicexml.ConnectionInformation;
import org.jvoicexml.JVoiceXmlMain;
import org.jvoicexml.JVoiceXmlMainListener;
import org.jvoicexml.Session;

import gov.iti.telecom.platform.AsteriskConnectionInformation;
import gov.iti.telecom.platform.AsteriskImplementationPlatformFactory;
import gov.iti.telecom.platform.MinimalConfiguration;

public class App {

    public static void main(String[] args) throws org.jvoicexml.event.ErrorEvent, Exception {
        try {
            System.out.println("Starting JVoiceXML runtime...");

            // 1. Create the JVoiceXML core runtime
            org.jvoicexml.Configuration config = new MinimalConfiguration();
            
            // Create and register the document server
            org.jvoicexml.documentserver.JVoiceXmlDocumentServer documentServer = new org.jvoicexml.documentserver.JVoiceXmlDocumentServer();
            documentServer.addSchemeStrategy(new org.jvoicexml.documentserver.schemestrategy.FileSchemeStrategy());
            
            // Create and set document storage (do NOT start it - JVoiceXML will start it)
            org.jvoicexml.documentserver.jetty.DocumentStorage storage = new org.jvoicexml.documentserver.jetty.DocumentStorage();
            storage.setStoragePort(8080); // or any available port
            documentServer.setDocumentStorage(storage);
            
            ((MinimalConfiguration) config).setDocumentServer(documentServer);
            
            JVoiceXmlMain jvxml = new JVoiceXmlMain(config);

            // Add a listener to know when the runtime has started
            CountDownLatch startedLatch = new CountDownLatch(1);
            CountDownLatch errorLatch = new CountDownLatch(1);

            jvxml.addListener(new JVoiceXmlMainListener() {
                @Override
                public void jvxmlStartupError(Throwable t) {
                    System.err.println("JVoiceXML startup error: " + t.getMessage());
                    t.printStackTrace();
                    errorLatch.countDown();
                }

                @Override
                public void jvxmlStarted() {
                    System.out.println("JVoiceXML runtime started successfully.");
                    startedLatch.countDown();
                }

                @Override
                public void jvxmlTerminated() {
                    System.out.println("JVoiceXML runtime terminated.");
                }
            });

            // 2. Create and set the implementation platform factory
            org.jvoicexml.ImplementationPlatformFactory platformFactory = new AsteriskImplementationPlatformFactory();
            jvxml.setImplementationPlatformFactory(platformFactory);

            // 3. Start the JVoiceXML main thread
            jvxml.start();

            // Wait for the runtime to start (or error)
            boolean started = startedLatch.await(10, TimeUnit.SECONDS);
            boolean errored = errorLatch.getCount() == 0;

            if (!started && !errored) {
                System.err.println("Timeout waiting for JVoiceXML to start.");
                return;
            }

            if (errored) {
                System.err.println("JVoiceXML failed to start.");
                return;
            }

            // 4. Create connection information for this session
            ConnectionInformation connectionInfo = new AsteriskConnectionInformation(
                    "default",      // profile
                    "dummy",        // system output
                    "dummy",        // user input
                    "dummy",        // call control
                    new URI("sip:1000"), // called device
                    new URI("sip:500"),  // calling device
                    "SIP",          // protocol name
                    "2.0"           // protocol version
            );

            // 5. Create a session
            Session session = jvxml.createSession(connectionInfo);
            System.out.println("Session created: " + session.getSessionId());

            // 6. Load and execute the VXML document
            File vxmlFile = new File("scenarios/hello.vxml");
            URI vxmlUri = vxmlFile.toURI();
            System.out.println("Loading VXML from: " + vxmlUri);

            session.call(vxmlUri);
            session.waitSessionEnd();

            System.out.println("Session ended. Last error: " + session.getLastError());

            // 7. Clean up
            jvxml.shutdown();
            
            // Wait for the session thread to finish
            while (jvxml.isAlive()) {
                Thread.sleep(100);
            }

            System.out.println("JVoiceXML runtime stopped.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
