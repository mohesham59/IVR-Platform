package gov.iti.telecom;

import org.asteriskjava.fastagi.AgiServer;
import org.asteriskjava.fastagi.DefaultAgiServer;

/**
 * FastAgiServerMain — entry point for the containerized FastAGI server.
 *
 * <p>Starts the Asterisk FastAGI server on port {@code 4573}. The handler
 * mapping is resolved from {@code fastagi-mapping.properties} on the classpath
 * (all paths map to {@link VxmlAgiHandler}).</p>
 *
 * <p>This is the class the Docker image runs, replacing the previous demo-only
 * {@link App} entry point that ran a single dummy JVoiceXML session and exited.</p>
 */
public class FastAgiServerMain {

    public static void main(String[] args) throws Exception {
        AgiServer server = new DefaultAgiServer();
        server.startup();
        System.out.println("FastAGI server started. Listening on port 4573.");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                server.shutdown();
            } catch (Exception e) {
                System.err.println("FastAGI server shutdown error: " + e.getMessage());
            }
        }, "fastagi-shutdown"));
    }
}
