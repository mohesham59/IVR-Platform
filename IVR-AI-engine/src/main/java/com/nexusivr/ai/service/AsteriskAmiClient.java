package com.nexusivr.ai.service;

import org.asteriskjava.manager.ManagerConnection;
import org.asteriskjava.manager.ManagerConnectionFactory;
import org.asteriskjava.manager.ManagerConnectionState;
import org.asteriskjava.manager.action.QueuePauseAction;
import org.asteriskjava.manager.action.QueueStatusAction;
import org.asteriskjava.manager.response.ManagerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AsteriskAmiClient {

    private static final Logger logger = LoggerFactory.getLogger(AsteriskAmiClient.class);
    private static AsteriskAmiClient instance;

    // AMI Live Cache Maps
    private final Map<String, LiveQueueStats> queueStatsMap = new ConcurrentHashMap<>();
    private final Map<String, String> agentStateCache = new ConcurrentHashMap<>();

    private final String amiHost;
    private final int amiPort;
    private final String amiUser;
    private final String amiPass;

    private volatile boolean connected = false;
    private ScheduledExecutorService executor;

    // Queue stats CLI cache
    private static final long QUEUE_STATS_CACHE_TTL_MS = 5000;
    private long lastQueueStatsRefresh = 0;

    public static class LiveQueueStats {
        public String queueName;
        public int waitingCalls = 0;
        public int avgWaitSeconds = 0;
        public int memberCount = 0;
        public int activeMembers = 0;
    }

    public AsteriskAmiClient() {
        this.amiHost = System.getenv().getOrDefault("AMI_HOST", "localhost");
        this.amiPort = Integer.parseInt(System.getenv().getOrDefault("AMI_PORT", "5038"));
        this.amiUser = System.getenv().getOrDefault("AMI_USER", "admin");
        this.amiPass = System.getenv().getOrDefault("AMI_PASS", "admin123");
    }

    public static synchronized AsteriskAmiClient getInstance() {
        if (instance == null) {
            instance = new AsteriskAmiClient();
        }
        return instance;
    }

    public LiveQueueStats getLiveStats(String queueName) {
        if (queueName == null) return new LiveQueueStats();
        return queueStatsMap.getOrDefault(queueName, new LiveQueueStats());
    }

    public String getAgentState(String agentIdOrInterface) {
        if (agentIdOrInterface == null) return "available";
        return agentStateCache.getOrDefault(agentIdOrInterface, "available");
    }

    /**
     * Parses a single key-value AMI header line and updates live cache.
     * Public for unit testing without live network connection.
     */
    public void parseAmiEventHeader(String eventType, Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) return;

        if ("QueueParams".equalsIgnoreCase(eventType) || "Event: QueueParams".equalsIgnoreCase(eventType)) {
            String queue = headers.get("Queue");
            if (queue != null) {
                LiveQueueStats stats = queueStatsMap.computeIfAbsent(queue, k -> new LiveQueueStats());
                stats.queueName = queue;
                if (headers.containsKey("Calls")) {
                    try { stats.waitingCalls = Integer.parseInt(headers.get("Calls")); } catch (Exception ignored) {}
                }
                if (headers.containsKey("Holdtime")) {
                    try { stats.avgWaitSeconds = Integer.parseInt(headers.get("Holdtime")); } catch (Exception ignored) {}
                }
                if (headers.containsKey("LoggedIn")) {
                    try { stats.memberCount = Integer.parseInt(headers.get("LoggedIn")); } catch (Exception ignored) {}
                }
                if (headers.containsKey("Available")) {
                    try { stats.activeMembers = Integer.parseInt(headers.get("Available")); } catch (Exception ignored) {}
                }
            }
        } else if ("QueueMember".equalsIgnoreCase(eventType) || "QueueMemberStatus".equalsIgnoreCase(eventType)) {
            String queue = headers.get("Queue");
            String location = headers.get("Location");
            String paused = headers.get("Paused");
            String statusStr = headers.get("Status");

            if (queue != null) {
                LiveQueueStats stats = queueStatsMap.computeIfAbsent(queue, k -> new LiveQueueStats());
                stats.queueName = queue;
            }

            if (location != null) {
                boolean isPaused = "1".equals(paused) || "true".equalsIgnoreCase(paused);
                String state = isPaused ? "paused" : "available";
                if ("2".equals(statusStr) || "InUse".equalsIgnoreCase(statusStr)) {
                    state = "in_call";
                } else if ("5".equals(statusStr) || "Unavailable".equalsIgnoreCase(statusStr)) {
                    state = "offline";
                }
                agentStateCache.put(location, state);
            }
        } else if ("QueueMemberPause".equalsIgnoreCase(eventType)) {
            String memberName = headers.get("MemberName");
            String location = headers.get("Location");
            String paused = headers.get("Paused");
            boolean isPaused = "1".equals(paused) || "true".equalsIgnoreCase(paused);
            String targetKey = location != null ? location : memberName;
            if (targetKey != null) {
                agentStateCache.put(targetKey, isPaused ? "paused" : "available");
            }
        }
    }

    /**
     * Sends QueuePause AMI action to pause/unpause an agent.
     */
    public boolean pauseAgent(String queueName, String interfaceUri, boolean paused, String reason) {
        logger.info("[AMI] Sending QueuePause: interface={}, queue={}, paused={}, reason={}", interfaceUri, queueName, paused, reason);
        try {
            ManagerConnectionFactory factory = new ManagerConnectionFactory(amiHost, amiPort, amiUser, amiPass);
            ManagerConnection conn = factory.createManagerConnection();
            conn.login();

            QueuePauseAction action = new QueuePauseAction();
            action.setInterface(interfaceUri);
            if (queueName != null && !queueName.isBlank()) {
                action.setQueue(queueName);
            }
            action.setPaused(paused);
            action.setReason(reason);

            ManagerResponse resp = conn.sendAction(action);
            conn.logoff();

            boolean success = "Success".equalsIgnoreCase(resp.getResponse());
            if (success) {
                agentStateCache.put(interfaceUri, paused ? "paused" : "available");
            }
            return success;
        } catch (Exception e) {
            logger.warn("[AMI] QueuePause action failed via asterisk-java, falling back to asterisk -rx CLI: {}", e.getMessage());
            return pauseAgentCliFallback(queueName, interfaceUri, paused, reason);
        }
    }

    private boolean pauseAgentCliFallback(String queueName, String interfaceUri, boolean paused, String reason) {
        try {
            String cmd = String.format("queue %s member %s", paused ? "pause" : "unpause", interfaceUri);
            ProcessBuilder pb = new ProcessBuilder("asterisk", "-rx", cmd);
            Process p = pb.start();
            int exit = p.waitFor();
            if (exit == 0) {
                agentStateCache.put(interfaceUri, paused ? "paused" : "available");
                return true;
            }
        } catch (Exception e) {
            logger.error("[AMI CLI Fallback] Error running CLI pause command: {}", e.getMessage());
        }
        return false;
    }

    public boolean addAgentToQueue(String queueName, String interfaceUri, int penalty) {
        logger.info("[AMI] Add agent to queue: queue={}, interface={}, penalty={}", queueName, interfaceUri, penalty);
        try {
            String cmd = String.format("queue add member %s to %s penalty %d", interfaceUri, queueName, penalty);
            ProcessBuilder pb = new ProcessBuilder("asterisk", "-rx", cmd);
            Process p = pb.start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            logger.warn("[AMI] addAgentToQueue failed: {}", e.getMessage());
            return false;
        }
    }

    public boolean removeAgentFromQueue(String queueName, String interfaceUri) {
        logger.info("[AMI] Remove agent from queue: queue={}, interface={}", queueName, interfaceUri);
        try {
            String cmd = String.format("queue remove member %s from %s", interfaceUri, queueName);
            ProcessBuilder pb = new ProcessBuilder("asterisk", "-rx", cmd);
            Process p = pb.start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            logger.warn("[AMI] removeAgentFromQueue failed: {}", e.getMessage());
            return false;
        }
    }

    public boolean checkConnection() {
        try (Socket socket = new Socket()) {
            socket.connect(new java.net.InetSocketAddress(amiHost, amiPort), 2000);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                String banner = reader.readLine();
                if (banner != null && banner.contains("Asterisk Call Manager")) {
                    this.connected = true;
                    return true;
                }
            }
        } catch (Exception e) {
            logger.debug("[AMI] Connection probe failed to {}:{}: {}", amiHost, amiPort, e.getMessage());
        }
        this.connected = false;
        return false;
    }

    public boolean isConnected() {
        return checkConnection();
    }

    /**
     * Queries Asterisk CLI for live queue statistics and updates the internal cache.
     * Parses "queue show" output to extract waiting calls, holdtime, and available agents.
     * Results are cached for 5 seconds to avoid excessive CLI calls.
     */
    public void refreshQueueStatsFromCli() {
        long now = System.currentTimeMillis();
        if (now - lastQueueStatsRefresh < QUEUE_STATS_CACHE_TTL_MS && !queueStatsMap.isEmpty()) {
            return;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("asterisk", "-rx", "queue show");
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                parseQueueShowOutput(output);
                lastQueueStatsRefresh = now;
            } else {
                logger.warn("[AMI CLI] queue show returned exit code: {}", exitCode);
            }
        } catch (Exception e) {
            logger.warn("[AMI CLI] Failed to query queue show: {}", e.getMessage());
        }
    }

    private void parseQueueShowOutput(String output) {
        if (output == null || output.isBlank()) return;

        String[] lines = output.split("\n");
        Pattern queuePattern = Pattern.compile(
            "^(.+?)\\s+has\\s+(\\d+)\\s+calls\\s+\\(max\\s+([^)]+)\\)\\s+in\\s+'([^']+)'\\s+strategy(?:\\s+\\(([^)]+)\\))?\\s*,\\s*W:(\\d+)\\s*,\\s*C:(\\d+)\\s*,\\s*A:(\\d+)"
        );

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("- ")) continue;

            Matcher matcher = queuePattern.matcher(trimmed);
            if (matcher.find()) {
                String queueName = matcher.group(1).trim();
                int waiting = Integer.parseInt(matcher.group(6));
                int available = Integer.parseInt(matcher.group(8));

                LiveQueueStats stats = queueStatsMap.computeIfAbsent(queueName, k -> new LiveQueueStats());
                stats.queueName = queueName;
                stats.waitingCalls = waiting;
                stats.activeMembers = available;

                // Parse holdtime from strategy options if present, e.g. "0s holdtime, 15s wrapup"
                String strategyOpts = matcher.group(5);
                if (strategyOpts != null && !strategyOpts.isBlank()) {
                    Pattern holdPattern = Pattern.compile("(\\d+)s\\s+holdtime");
                    Matcher holdMatcher = holdPattern.matcher(strategyOpts);
                    if (holdMatcher.find()) {
                        stats.avgWaitSeconds = Integer.parseInt(holdMatcher.group(1));
                    }
                }
            }
        }
    }

    public Map<String, Object> getAmiHealthStatus() {
        boolean isConn = checkConnection();
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("host", amiHost);
        status.put("port", amiPort);
        status.put("connected", isConn);
        status.put("status", isConn ? "HEALTHY" : "OFFLINE");
        return status;
    }
}
