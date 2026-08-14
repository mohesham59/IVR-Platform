package com.nexusivr.ai.service;

import com.nexusivr.ai.dao.SipExtensionDao;
import com.nexusivr.ai.exception.ServiceException;
import com.nexusivr.ai.exception.ValidationException;
import com.nexusivr.ai.model.SipExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SipExtensionService {

    private static final Logger logger = LoggerFactory.getLogger(SipExtensionService.class);
    private static final String PJSIP_CONF_PATH = "/etc/asterisk/pjsip.conf";
    private static final long CACHE_TTL_MS = 5000; // 5 seconds cache TTL

    private final SipExtensionDao sipExtensionDao;

    // In-memory cache for live status
    private static final Map<String, EndpointStatus> statusCache = new ConcurrentHashMap<>();
    private static long lastCacheTimestamp = 0;

    public SipExtensionService(SipExtensionDao sipExtensionDao) {
        this.sipExtensionDao = sipExtensionDao;
    }

    public SipExtensionService() {
        this(new SipExtensionDao());
    }

    public static class EndpointStatus {
        public String registrationStatus = "Offline";
        public String callStatus = "Idle";
        public int activeChannels = 0;
    }

    public List<SipExtension> getSipExtensions(UUID tenantId) {
        if (tenantId == null) {
            return Collections.emptyList();
        }
        List<SipExtension> dbExtensions = sipExtensionDao.findByTenantId(tenantId);
        Map<String, EndpointStatus> liveStatusMap = getLiveStatusMap();

        for (SipExtension ext : dbExtensions) {
            EndpointStatus live = liveStatusMap.get(ext.getExtensionNumber());
            if (live != null) {
                ext.setRegistrationStatus(live.registrationStatus);
                ext.setCallStatus(live.callStatus);
                ext.setLiveChannels(live.activeChannels);
            } else {
                ext.setRegistrationStatus("Offline");
                ext.setCallStatus("Idle");
                ext.setLiveChannels(0);
            }
        }
        return dbExtensions;
    }

    public SipExtension createSipExtension(UUID tenantId, String extNum, String displayName, String password, boolean tlsEnabled) {
        if (tenantId == null) throw new ValidationException("Tenant ID is required");
        if (extNum == null || extNum.isBlank()) throw new ValidationException("Extension number is required");
        if (displayName == null || displayName.isBlank()) throw new ValidationException("Display name is required");
        if (password == null || password.isBlank()) throw new ValidationException("SIP password is required");

        String cleanExt = extNum.trim();
        if (!cleanExt.matches("^[0-9]{3,10}$")) {
            throw new ValidationException("Extension number must contain 3 to 10 digits");
        }

        if (sipExtensionDao.existsByExtension(tenantId, cleanExt)) {
            throw new ValidationException("Extension number '" + cleanExt + "' already exists for this tenant");
        }

        // 1. Write PJSIP endpoint block to /etc/asterisk/pjsip.conf
        provisionPjsipEndpoint(cleanExt, password.trim(), tlsEnabled);

        // 2. Reload PJSIP module in Asterisk
        reloadAsteriskPjsip();

        // 3. Save DB record
        SipExtension newExt = new SipExtension();
        newExt.setTenantId(tenantId);
        newExt.setExtensionNumber(cleanExt);
        newExt.setDisplayName(displayName.trim());
        newExt.setSipPassword(password.trim());
        newExt.setTlsEnabled(tlsEnabled);

        SipExtension saved = sipExtensionDao.save(newExt);

        // Invalidate cache immediately so new extension is queried on next request
        invalidateCache();
        return saved;
    }

    public boolean deleteSipExtension(UUID tenantId, UUID id) {
        if (tenantId == null || id == null) throw new ValidationException("Tenant ID and Extension ID are required");

        SipExtension existing = sipExtensionDao.findById(tenantId, id);
        if (existing == null) {
            throw new ValidationException("SIP Extension not found for this tenant");
        }

        // 1. Remove PJSIP block from /etc/asterisk/pjsip.conf
        removePjsipEndpoint(existing.getExtensionNumber());

        // 2. Reload Asterisk PJSIP
        reloadAsteriskPjsip();

        // 3. Delete DB record
        boolean deleted = sipExtensionDao.delete(tenantId, id);
        invalidateCache();
        return deleted;
    }

    /**
     * Appends a new PJSIP endpoint configuration to /etc/asterisk/pjsip.conf
     */
    private synchronized void provisionPjsipEndpoint(String extNum, String password, boolean tlsEnabled) {
        try {
            Path pjsipPath = Paths.get(PJSIP_CONF_PATH);
            if (!Files.exists(pjsipPath)) {
                logger.warn("pjsip.conf not found at {}. Skipping file update.", PJSIP_CONF_PATH);
                return;
            }

            // Remove any old config for this extension if present to avoid duplication
            removePjsipEndpoint(extNum);

            String transport = tlsEnabled ? "transport-tls" : "transport-udp";

            String pjsipBlock = String.format(
                    "\n; Tenant SIP Extension %s\n" +
                    "[%s]\n" +
                    "type=endpoint\n" +
                    "context=default\n" +
                    "disallow=all\n" +
                    "allow=ulaw,alaw\n" +
                    "auth=auth%s\n" +
                    "aors=%s\n" +
                    "transport=%s\n" +
                    "\n" +
                    "[auth%s]\n" +
                    "type=auth\n" +
                    "auth_type=userpass\n" +
                    "username=%s\n" +
                    "password=%s\n" +
                    "\n" +
                    "[%s]\n" +
                    "type=aor\n" +
                    "max_contacts=5\n",
                    extNum, extNum, extNum, extNum, transport, extNum, extNum, password, extNum
            );

            Files.writeString(pjsipPath, pjsipBlock, StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
            logger.info("Successfully appended PJSIP endpoint block for extension {} into {}", extNum, PJSIP_CONF_PATH);
        } catch (Exception e) {
            logger.error("Error provisioning PJSIP endpoint for ext {}: {}", extNum, e.getMessage(), e);
            throw new ServiceException("Failed to provision Asterisk PJSIP endpoint: " + e.getMessage());
        }
    }

    /**
     * Removes PJSIP endpoint sections for an extension from /etc/asterisk/pjsip.conf
     */
    private synchronized void removePjsipEndpoint(String extNum) {
        try {
            Path pjsipPath = Paths.get(PJSIP_CONF_PATH);
            if (!Files.exists(pjsipPath)) return;

            List<String> lines = Files.readAllLines(pjsipPath, StandardCharsets.UTF_8);
            List<String> filtered = new ArrayList<>();

            boolean skipping = false;
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.equalsIgnoreCase("[" + extNum + "]") ||
                    trimmed.equalsIgnoreCase("[auth" + extNum + "]") ||
                    trimmed.contains("Tenant SIP Extension " + extNum)) {
                    skipping = true;
                    continue;
                }
                if (skipping && trimmed.startsWith("[") && trimmed.endsWith("]")) {
                    skipping = false;
                }
                if (!skipping) {
                    filtered.add(line);
                }
            }

            Files.write(pjsipPath, filtered, StandardCharsets.UTF_8);
            logger.info("Removed PJSIP configuration block for extension {}", extNum);
        } catch (Exception e) {
            logger.error("Error removing PJSIP endpoint ext {}: {}", extNum, e.getMessage());
        }
    }

    /**
     * Reloads PJSIP in Asterisk via `asterisk -rx "pjsip reload"`
     */
    private void reloadAsteriskPjsip() {
        try {
            ProcessBuilder pb = new ProcessBuilder("asterisk", "-rx", "pjsip reload");
            Process p = pb.start();
            int exitCode = p.waitFor();
            if (exitCode == 0) {
                logger.info("Asterisk PJSIP module reloaded successfully.");
            } else {
                logger.warn("asterisk -rx 'pjsip reload' returned non-zero exit code: {}", exitCode);
            }
        } catch (Exception e) {
            logger.warn("Could not execute Asterisk PJSIP reload command: {}", e.getMessage());
        }
    }

    /**
     * Queries Asterisk for live endpoint statuses using `asterisk -rx "pjsip show endpoints"` with in-memory caching.
     */
    private synchronized Map<String, EndpointStatus> getLiveStatusMap() {
        long now = System.currentTimeMillis();
        if (now - lastCacheTimestamp < CACHE_TTL_MS && !statusCache.isEmpty()) {
            return statusCache;
        }

        Map<String, EndpointStatus> newMap = new HashMap<>();
        try {
            ProcessBuilder pb = new ProcessBuilder("asterisk", "-rx", "pjsip show endpoints");
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                String currentEndpoint = null;
                EndpointStatus currentStatus = null;

                // Example Regex matching:
                //  Endpoint:  1001                                                 Not in use    0 of inf
                //  Endpoint:  1002                                                 Unavailable   0 of inf
                Pattern endpointPattern = Pattern.compile("^\\s*Endpoint:\\s+([A-Za-z0-9_]+)\\s+([A-Za-z0-9_ ]+?)\\s+(\\d+)\\s+of\\s+");
                Pattern contactPattern = Pattern.compile("^\\s*Contact:\\s+.*?\\s+(Reachable|Available|Unreachable|Unknown)");

                while ((line = reader.readLine()) != null) {
                    Matcher epMatcher = endpointPattern.matcher(line);
                    if (epMatcher.find()) {
                        if (currentEndpoint != null && currentStatus != null) {
                            newMap.put(currentEndpoint, currentStatus);
                        }
                        currentEndpoint = epMatcher.group(1);
                        String rawState = epMatcher.group(2).trim();
                        int channels = Integer.parseInt(epMatcher.group(3));

                        currentStatus = new EndpointStatus();
                        currentStatus.activeChannels = channels;

                        if ("Unavailable".equalsIgnoreCase(rawState)) {
                            currentStatus.registrationStatus = "Offline";
                        } else {
                            currentStatus.registrationStatus = "Registered";
                        }

                        if (channels > 0 || "In use".equalsIgnoreCase(rawState) || "Ringing".equalsIgnoreCase(rawState)) {
                            currentStatus.callStatus = "In Call";
                        } else {
                            currentStatus.callStatus = "Idle";
                        }
                    } else if (contactPattern.matcher(line).find() && currentStatus != null) {
                        currentStatus.registrationStatus = "Registered";
                    }
                }
                if (currentEndpoint != null && currentStatus != null) {
                    newMap.put(currentEndpoint, currentStatus);
                }
            }
            process.waitFor();
            statusCache.clear();
            statusCache.putAll(newMap);
            lastCacheTimestamp = now;
        } catch (Exception e) {
            logger.warn("Error querying Asterisk PJSIP live endpoints: {}", e.getMessage());
        }
        return statusCache;
    }

    public static void invalidateCache() {
        lastCacheTimestamp = 0;
        statusCache.clear();
    }
}
