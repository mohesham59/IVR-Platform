package com.nexusivr.ai.service;

import com.nexusivr.ai.dao.AgentStateDao;
import com.nexusivr.ai.dao.QueueDao;
import com.nexusivr.ai.exception.ServiceException;
import com.nexusivr.ai.exception.ValidationException;
import com.nexusivr.ai.model.AgentStateRecord;
import com.nexusivr.ai.model.Queue;
import com.nexusivr.ai.model.QueueMember;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class QueueService {

    private static final Logger logger = LoggerFactory.getLogger(QueueService.class);
    private static final String QUEUES_CONF_PATH = "/etc/asterisk/queues.conf";

    private final QueueDao queueDao;
    private final AgentStateDao agentStateDao;
    private final AsteriskAmiClient amiClient;

    public QueueService(QueueDao queueDao, AgentStateDao agentStateDao, AsteriskAmiClient amiClient) {
        this.queueDao = queueDao;
        this.agentStateDao = agentStateDao;
        this.amiClient = amiClient;
    }

    public QueueService() {
        this(new QueueDao(), new AgentStateDao(), AsteriskAmiClient.getInstance());
    }

    public List<Queue> getQueues(UUID tenantId) {
        if (tenantId == null) return Collections.emptyList();
        List<Queue> queues = queueDao.findByTenantId(tenantId);

        // Refresh live queue stats from Asterisk CLI
        amiClient.refreshQueueStatsFromCli();

        for (Queue q : queues) {
            AsteriskAmiClient.LiveQueueStats live = amiClient.getLiveStats(q.getName());
            if (live != null) {
                q.setWaitingCalls(live.waitingCalls);
                q.setAvgWaitSeconds(live.avgWaitSeconds);
                q.setActiveMembers(live.activeMembers);
            }
            List<QueueMember> members = queueDao.getMembers(q.getId());
            q.setMemberCount(members.size());
        }
        return queues;
    }

    public Map<String, Object> getQueueDetail(UUID tenantId, UUID queueId) {
        if (tenantId == null || queueId == null) throw new ValidationException("Tenant ID and Queue ID are required");

        Queue queue = queueDao.findById(tenantId, queueId);
        if (queue == null) throw new ValidationException("Queue not found");

        // Refresh live queue stats from Asterisk CLI
        amiClient.refreshQueueStatsFromCli();

        AsteriskAmiClient.LiveQueueStats live = amiClient.getLiveStats(queue.getName());
        if (live != null) {
            queue.setWaitingCalls(live.waitingCalls);
            queue.setAvgWaitSeconds(live.avgWaitSeconds);
            queue.setActiveMembers(live.activeMembers);
        }

        List<QueueMember> members = queueDao.getMembers(queueId);
        queue.setMemberCount(members.size());

        Map<String, Object> result = new HashMap<>();
        result.put("queue", queue);
        result.put("members", members);
        return result;
    }

    public Queue createQueue(UUID tenantId, Queue queue) {
        if (tenantId == null) throw new ValidationException("Tenant ID is required");
        if (queue.getName() == null || queue.getName().isBlank()) throw new ValidationException("Queue name is required");

        queue.setTenantId(tenantId);
        String name = queue.getName().trim();
        queue.setName(name);

        String strategy = queue.getStrategy();
        if (strategy == null || strategy.isBlank()) {
            strategy = "round_robin";
        }
        queue.setStrategy(strategy);

        // 1. Provision block in /etc/asterisk/queues.conf
        provisionQueuesConf(name, strategy, queue.getWrapUpTimeSeconds(), queue.getMusicOnHold());

        // 2. Reload Asterisk app_queue
        reloadAsteriskQueues();

        // 3. Save DB record
        return queueDao.save(queue);
    }

    public Queue updateQueue(UUID tenantId, UUID id, Queue queue) {
        if (tenantId == null || id == null) throw new ValidationException("Tenant ID and Queue ID are required");

        Queue existing = queueDao.findById(tenantId, id);
        if (existing == null) throw new ValidationException("Queue not found");

        existing.setName(queue.getName() != null ? queue.getName().trim() : existing.getName());
        existing.setStrategy(queue.getStrategy() != null ? queue.getStrategy() : existing.getStrategy());
        existing.setWrapUpTimeSeconds(queue.getWrapUpTimeSeconds() > 0 ? queue.getWrapUpTimeSeconds() : existing.getWrapUpTimeSeconds());
        existing.setMaxWaitSeconds(queue.getMaxWaitSeconds() > 0 ? queue.getMaxWaitSeconds() : existing.getMaxWaitSeconds());
        existing.setMusicOnHold(queue.getMusicOnHold() != null ? queue.getMusicOnHold() : existing.getMusicOnHold());
        existing.setOverflowAction(queue.getOverflowAction() != null ? queue.getOverflowAction() : existing.getOverflowAction());
        existing.setStatus(queue.getStatus() != null ? queue.getStatus() : existing.getStatus());

        provisionQueuesConf(existing.getName(), existing.getStrategy(), existing.getWrapUpTimeSeconds(), existing.getMusicOnHold());
        reloadAsteriskQueues();

        boolean updated = queueDao.update(tenantId, id, existing);
        if (!updated) throw new ServiceException("Failed to update queue in database");

        return queueDao.findById(tenantId, id);
    }

    public boolean deleteQueue(UUID tenantId, UUID id) {
        if (tenantId == null || id == null) throw new ValidationException("Tenant ID and Queue ID are required");

        Queue existing = queueDao.findById(tenantId, id);
        if (existing == null) throw new ValidationException("Queue not found");

        removeQueuesConf(existing.getName());
        reloadAsteriskQueues();

        // Clean up orphaned agent states bound to deleted queue
        agentStateDao.cleanUpAgentsForDeletedQueue(id);

        return queueDao.delete(tenantId, id);
    }

    public QueueMember addQueueMember(UUID tenantId, UUID queueId, UUID agentId, int penalty) {
        if (tenantId == null || queueId == null || agentId == null) throw new ValidationException("Tenant ID, Queue ID, and Agent ID are required");

        Queue queue = queueDao.findById(tenantId, queueId);
        if (queue == null) throw new ValidationException("Queue not found for this tenant");

        QueueMember member = queueDao.addMember(queueId, agentId, penalty);

        // Optionally add member dynamically to Asterisk queue via AMI
        String interfaceUri = "Local/" + agentId.toString().substring(0, 8) + "@default";
        amiClient.addAgentToQueue(queue.getName(), interfaceUri, penalty);

        return member;
    }

    public boolean removeQueueMember(UUID tenantId, UUID queueId, UUID agentId) {
        if (tenantId == null || queueId == null || agentId == null) throw new ValidationException("Tenant ID, Queue ID, and Agent ID are required");

        Queue queue = queueDao.findById(tenantId, queueId);
        if (queue == null) throw new ValidationException("Queue not found for this tenant");

        boolean removed = queueDao.removeMember(queueId, agentId);

        String interfaceUri = "Local/" + agentId.toString().substring(0, 8) + "@default";
        amiClient.removeAgentFromQueue(queue.getName(), interfaceUri);

        return removed;
    }

    public AgentStateRecord updateAgentState(UUID tenantId, UUID agentId, String newState) {
        if (tenantId == null || agentId == null) throw new ValidationException("Tenant ID and Agent ID are required");

        String cleanState = newState != null ? newState.toLowerCase().trim() : "available";
        if (!Set.of("available", "in_call", "paused", "offline").contains(cleanState)) {
            throw new ValidationException("Invalid state. Allowed: available, in_call, paused, offline");
        }

        boolean paused = "paused".equals(cleanState);
        String interfaceUri = "Local/" + agentId.toString().substring(0, 8) + "@default";

        // Issue AMI QueuePause action
        amiClient.pauseAgent(null, interfaceUri, paused, "UI state change to " + cleanState);

        // Update DB
        boolean updated = agentStateDao.updateAgentState(agentId, cleanState, null);
        if (!updated) throw new ServiceException("Failed to update agent state in database");

        return agentStateDao.getAgentState(agentId);
    }

    public List<AgentStateRecord> getTenantAgents(UUID tenantId) {
        if (tenantId == null) return Collections.emptyList();
        return agentStateDao.getTenantAgentsWithState(tenantId);
    }

    private synchronized void provisionQueuesConf(String queueName, String strategy, int wrapUpTime, String musicOnHold) {
        try {
            Path path = Paths.get(QUEUES_CONF_PATH);
            if (!Files.exists(path)) return;

            removeQueuesConf(queueName);

            String astStrategy = switch (strategy) {
                case "least_recent" -> "leastrecent";
                case "ring_all" -> "ringall";
                case "linear" -> "linear";
                default -> "roundrobin";
            };

            String block = String.format(
                    "\n; Queue %s\n" +
                    "[%s]\n" +
                    "strategy=%s\n" +
                    "timeout=30\n" +
                    "wrapuptime=%d\n" +
                    "autofill=yes\n" +
                    "musicclass=%s\n",
                    queueName, queueName, astStrategy, wrapUpTime > 0 ? wrapUpTime : 15,
                    musicOnHold != null ? musicOnHold : "default"
            );

            Files.writeString(path, block, StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
            logger.info("Successfully provisioned queue block [{}] in {}", queueName, QUEUES_CONF_PATH);
        } catch (Exception e) {
            logger.error("Error provisioning queue {} in {}: {}", queueName, QUEUES_CONF_PATH, e.getMessage());
        }
    }

    private synchronized void removeQueuesConf(String queueName) {
        try {
            Path path = Paths.get(QUEUES_CONF_PATH);
            if (!Files.exists(path)) return;

            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            List<String> filtered = new ArrayList<>();

            boolean skipping = false;
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.equalsIgnoreCase("[" + queueName + "]") || trimmed.contains("; Queue " + queueName)) {
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

            Files.write(path, filtered, StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.error("Error removing queue {} from {}: {}", queueName, QUEUES_CONF_PATH, e.getMessage());
        }
    }

    private void reloadAsteriskQueues() {
        try {
            ProcessBuilder pb = new ProcessBuilder("asterisk", "-rx", "module reload app_queue.so");
            Process p = pb.start();
            p.waitFor();
        } catch (Exception e) {
            logger.warn("Could not execute Asterisk queue reload command: {}", e.getMessage());
        }
    }
}
