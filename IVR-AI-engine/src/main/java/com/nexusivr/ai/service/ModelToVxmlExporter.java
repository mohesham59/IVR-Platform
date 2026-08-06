package com.nexusivr.ai.service;

import com.nexusivr.ai.model.flow.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Deterministic VoiceXML 2.1 generator.
 * <p>
 * Converts a {@link FlowModel} into valid VoiceXML 2.1 XML.
 * This class contains NO AI/LLM calls. It is a pure deterministic renderer.
 * </p>
 *
 * <p>Algorithm:</p>
 * <ol>
 *   <li><b>Initialize</b> — Write XML declaration and opening {@code <vxml>} tag.</li>
 *   <li><b>Build outgoing edge lookup</b> — Map sourceNodeId → List{@code <}{@code FlowConnection} for O(1) access.</li>
 *   <li><b>Render each node</b> — For each {@link FlowNode} in model order, render a {@code <form>} with content
 *       determined by node type and outgoing connections.</li>
 *   <li><b>Close</b> — Write closing {@code </vxml>} tag.</li>
 * </ol>
 *
 * <p>Supported node types:</p>
 * <ul>
 *   <li>{@code START} — {@code <block><prompt>…</prompt><goto/></block>}</li>
 *   <li>{@code PROMPT / GREETING} — {@code <block><prompt>…</prompt><goto/></block>}</li>
 *   <li>{@code MENU} — {@code <menu><prompt>…</prompt><choice …>…</choice></menu>}</li>
 *   <li>{@code INPUT} — {@code <field><prompt>…</prompt><grammar>…</grammar><filled><goto/></filled><noinput><goto/></noinput><nomatch><goto/></nomatch></field>}</li>
 *   <li>{@code TRANSFER} — {@code <block><transfer dest="…"/></block>}</li>
 *   <li>{@code QUEUE} — {@code <block><prompt>…</prompt><goto/></block>}</li>
 *   <li>{@code CONDITION} — {@code <block><if cond="…"><goto/></if><else><goto/></else></block>}</li>
 *   <li>{@code BUSINESS_HOURS} — {@code <block><if cond="…"><goto/></if><else><goto/></else></block>}</li>
 *   <li>{@code END / DISCONNECT} — {@code <block><prompt>…</prompt><disconnect/></block>}</li>
 *   <li>{@code RECORDING} — {@code <record name="…" …><prompt>…</prompt></record>}</li>
 *   <li>{@code API / DATABASE / WEBHOOK / AI / VOICEMAIL} — {@code <block><prompt>…</prompt><goto/></block>}</li>
 * </ul>
 */
public class ModelToVxmlExporter {

    private static final Logger logger = LoggerFactory.getLogger(ModelToVxmlExporter.class);

    public String export(FlowModel model) {
        if (model == null || model.getNodes().isEmpty()) {
            return minimalVxml();
        }

        logger.info("[ModelToVxmlExporter] Exporting FlowModel to VoiceXML: nodes={}, connections={}",
                model.getNodes().size(), model.getConnections().size());

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        String version = model.getVoicexmlVersion();
        if (version == null || version.isBlank()) {
            version = "2.1";
        }
        sb.append("<vxml version=\"").append(escapeXml(version)).append("\" xmlns=\"http://www.w3.org/2001/vxml\">\n");
        if (model.getName() != null && !model.getName().isBlank()) {
            sb.append("  <meta name=\"flow-name\" content=\"").append(escapeXml(model.getName())).append("\"/>\n");
        }

        Map<String, List<FlowConnection>> outgoing = new HashMap<>();
        for (FlowConnection conn : model.getConnections()) {
            outgoing.computeIfAbsent(conn.getSourceNodeId(), k -> new ArrayList<>()).add(conn);
        }

        for (FlowNode node : model.getNodes()) {
            renderNode(sb, node, outgoing);
        }

        sb.append("</vxml>");
        return sb.toString();
    }

    private void renderNode(StringBuilder sb, FlowNode node, Map<String, List<FlowConnection>> outgoing) {
        sb.append("  <!-- ").append(escapeXml(node.getTitle() != null ? node.getTitle() : node.getType().name())).append(" (")
          .append(escapeXml(node.getId())).append(") -->\n");
        sb.append("  <form id=\"").append(escapeXml(node.getId())).append("\">\n");

        List<FlowConnection> nodeOutgoing = outgoing.getOrDefault(node.getId(), List.of());

        switch (node.getType()) {
            case START -> renderStart(sb, node, nodeOutgoing);
            case PROMPT -> renderPrompt(sb, node, nodeOutgoing);
            case MENU -> renderMenu(sb, node, nodeOutgoing);
            case INPUT -> renderInput(sb, node, nodeOutgoing);
            case TRANSFER -> renderTransfer(sb, node, nodeOutgoing);
            case QUEUE -> renderQueue(sb, node, nodeOutgoing);
            case CONDITION -> renderCondition(sb, node, nodeOutgoing);
            case BUSINESS_HOURS -> renderBusinessHours(sb, node, nodeOutgoing);
            case HOLIDAY -> renderHoliday(sb, node, nodeOutgoing);
            case END -> renderEnd(sb, node, nodeOutgoing);
            case DISCONNECT -> renderDisconnect(sb, node, nodeOutgoing);
            case RECORDING -> renderRecording(sb, node, nodeOutgoing);
            case API -> renderServiceBlock(sb, node, nodeOutgoing, "API");
            case DATABASE -> renderServiceBlock(sb, node, nodeOutgoing, "Database");
            case WEBHOOK -> renderServiceBlock(sb, node, nodeOutgoing, "Webhook");
            case AI -> renderAi(sb, node, nodeOutgoing);
            case VOICEMAIL -> renderVoicemail(sb, node, nodeOutgoing);
            default -> renderDefault(sb, node, nodeOutgoing);
        }

        sb.append("  </form>\n");
    }

    private void renderStart(StringBuilder sb, FlowNode node, List<FlowConnection> outgoing) {
        renderPromptAndGoto(sb, node, outgoing, false);
    }

    private void renderPrompt(StringBuilder sb, FlowNode node, List<FlowConnection> outgoing) {
        renderPromptAndGoto(sb, node, outgoing, false);
    }

    private void renderPromptAndGoto(StringBuilder sb, FlowNode node, List<FlowConnection> outgoing, boolean isBlock) {
        if (isBlock) {
            sb.append("    <block>\n");
        }
        sb.append("      <prompt>").append(escapeXml(getNodePrompt(node))).append("</prompt>\n");
        for (FlowConnection conn : outgoing) {
            if (conn.getTargetNodeId() != null && !conn.getTargetNodeId().isBlank()) {
                sb.append("      <goto next=\"#").append(escapeXml(conn.getTargetNodeId())).append("\"/>\n");
                break;
            }
        }
        if (isBlock) {
            sb.append("    </block>\n");
        }
    }

    private void renderMenu(StringBuilder sb, FlowNode node, List<FlowConnection> outgoing) {
        sb.append("    <menu>\n");
        String promptText = getNodePrompt(node);
        if (promptText != null && !promptText.isBlank()) {
            sb.append("      <prompt>").append(escapeXml(promptText)).append("</prompt>\n");
        }

        Map<String, String> seenDtmfToTarget = new HashMap<>();

        if (node.getMenu() != null && !node.getMenu().getChoices().isEmpty()) {
            for (FlowChoice choice : node.getMenu().getChoices()) {
                String dtmf = choice.getDtmf() != null && !choice.getDtmf().isBlank()
                        ? choice.getDtmf().trim()
                        : extractDtmfFromKey(choice.getKey());
                if (dtmf == null || dtmf.isBlank()) {
                    dtmf = "1";
                }

                String target = choice.getTargetNodeId();
                if (target == null || target.isBlank()) {
                    target = outgoing.stream().findFirst().map(FlowConnection::getTargetNodeId).orElse(null);
                }

                if (seenDtmfToTarget.containsKey(dtmf)) {
                    if (Objects.equals(seenDtmfToTarget.get(dtmf), target)) {
                        continue;
                    }
                    String[] allDtmfs = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "*", "#"};
                    boolean allocated = false;
                    for (String candidate : allDtmfs) {
                        if (!seenDtmfToTarget.containsKey(candidate)) {
                            dtmf = candidate;
                            allocated = true;
                            break;
                        }
                    }
                    if (!allocated) {
                        continue;
                    }
                }
                seenDtmfToTarget.put(dtmf, target);

                sb.append("      <choice dtmf=\"").append(escapeXml(dtmf)).append("\"");
                if (target != null && !target.isBlank()) {
                    sb.append(" next=\"#").append(escapeXml(target)).append("\"");
                }

                String label = choice.getLabel();
                boolean isPlaceholderOrNarration = isPlaceholderOrNarrationLabel(label, dtmf, choice.getKey(), promptText);

                if (label != null && !label.isBlank() && !isPlaceholderOrNarration) {
                    sb.append(">").append(escapeXml(label)).append("</choice>\n");
                } else {
                    sb.append("/>\n");
                }
            }
        } else {
            int digitCounter = 1;
            for (FlowConnection conn : outgoing) {
                if (conn.getTargetNodeId() != null && !conn.getTargetNodeId().isBlank()) {
                    String dtmf = extractDtmfFromKey(conn.getSourcePort());
                    if ("1".equals(dtmf) && seenDtmfToTarget.containsKey("1")) {
                        dtmf = String.valueOf(digitCounter);
                    }
                    if (seenDtmfToTarget.containsKey(dtmf)) {
                        digitCounter++;
                        dtmf = String.valueOf(digitCounter);
                    }
                    seenDtmfToTarget.put(dtmf, conn.getTargetNodeId());

                    sb.append("      <choice dtmf=\"").append(escapeXml(dtmf))
                      .append("\" next=\"#").append(escapeXml(conn.getTargetNodeId())).append("\"/>\n");
                    digitCounter++;
                }
            }
        }

        sb.append("    </menu>\n");
    }

    private boolean isPlaceholderOrNarrationLabel(String label, String dtmf, String key, String promptText) {
        if (label == null || label.isBlank()) {
            return true;
        }
        String trimmed = label.trim();
        if (promptText != null && !promptText.isBlank()) {
            String pTrimmed = promptText.trim();
            if (trimmed.equalsIgnoreCase(pTrimmed)) {
                return true;
            }
            if (pTrimmed.contains(trimmed) && trimmed.length() > 20) {
                return true;
            }
            if (trimmed.contains(pTrimmed) && pTrimmed.length() > 20) {
                return true;
            }
        }
        if (trimmed.matches("(?i)^option\\s*(key)?\\s*\\d*$")) {
            return true;
        }
        if (trimmed.equalsIgnoreCase("Option") || trimmed.equalsIgnoreCase("Choice")) {
            return true;
        }
        if (key != null && (trimmed.equalsIgnoreCase(key.trim()) || trimmed.equalsIgnoreCase("key" + key.trim()))) {
            return true;
        }
        if (dtmf != null && (trimmed.equalsIgnoreCase("key" + dtmf.trim()) || trimmed.equalsIgnoreCase("digit " + dtmf.trim()))) {
            return true;
        }
        return false;
    }

    private void renderInput(StringBuilder sb, FlowNode node, List<FlowConnection> outgoing) {
        sb.append("    <field name=\"").append(escapeXml(node.getId())).append("\"");
        if (node.getInput() != null && node.getInput().getDigits() > 0) {
            sb.append(" type=\"").append(node.getInput().getDigits()).append("\"");
        }
        sb.append(">\n");

        sb.append("      <prompt>").append(escapeXml(getNodePrompt(node))).append("</prompt>\n");

        sb.append("      <grammar mode=\"dtmf\" version=\"1.0\">\n");
        sb.append("        <rule id=\"digits\"><one-of>\n");
        for (int d = 0; d <= 9; d++) {
            sb.append("          <item>").append(d).append("</item>\n");
        }
        sb.append("        </one-of></rule>\n");
        sb.append("      </grammar>\n");

        List<FlowConnection> successConns = outgoing.stream()
                .filter(c -> "success".equalsIgnoreCase(c.getSourcePort()) || "out".equalsIgnoreCase(c.getSourcePort()))
                .toList();
        List<FlowConnection> timeoutConns = outgoing.stream()
                .filter(c -> "timeout".equalsIgnoreCase(c.getSourcePort()))
                .toList();
        List<FlowConnection> errorConns = outgoing.stream()
                .filter(c -> "error".equalsIgnoreCase(c.getSourcePort()) || "invalid".equalsIgnoreCase(c.getSourcePort()))
                .toList();

        if (!successConns.isEmpty() || !outgoing.isEmpty()) {
            sb.append("      <filled>\n");
            List<FlowConnection> filledTargets = !successConns.isEmpty() ? successConns : outgoing;
            for (FlowConnection conn : filledTargets) {
                if (conn.getTargetNodeId() != null && !conn.getTargetNodeId().isBlank()) {
                    sb.append("        <goto next=\"#").append(escapeXml(conn.getTargetNodeId())).append("\"/>\n");
                    break;
                }
            }
            sb.append("      </filled>\n");
        }

        if (!timeoutConns.isEmpty()) {
            sb.append("      <noinput>\n");
            for (FlowConnection conn : timeoutConns) {
                if (conn.getTargetNodeId() != null && !conn.getTargetNodeId().isBlank()) {
                    sb.append("        <goto next=\"#").append(escapeXml(conn.getTargetNodeId())).append("\"/>\n");
                    break;
                }
            }
            sb.append("      </noinput>\n");
        }

        if (!errorConns.isEmpty()) {
            sb.append("      <nomatch>\n");
            for (FlowConnection conn : errorConns) {
                if (conn.getTargetNodeId() != null && !conn.getTargetNodeId().isBlank()) {
                    sb.append("        <goto next=\"#").append(escapeXml(conn.getTargetNodeId())).append("\"/>\n");
                    break;
                }
            }
            sb.append("      </nomatch>\n");
        }

        sb.append("    </field>\n");
    }

    private void renderTransfer(StringBuilder sb, FlowNode node, List<FlowConnection> outgoing) {
        sb.append("    <block>\n");
        sb.append("      <prompt>").append(escapeXml(getNodePrompt(node))).append("</prompt>\n");
        String dest = (node.getTransfer() != null && node.getTransfer().getDestination() != null)
                ? node.getTransfer().getDestination()
                : "TRANSFER_TARGET_PLACEHOLDER";
        sb.append("      <transfer dest=\"").append(escapeXml(dest)).append("\"/>\n");
        for (FlowConnection conn : outgoing) {
            if (conn.getTargetNodeId() != null && !conn.getTargetNodeId().isBlank()) {
                sb.append("      <goto next=\"#").append(escapeXml(conn.getTargetNodeId())).append("\"/>\n");
                break;
            }
        }
        sb.append("    </block>\n");
    }

    private void renderQueue(StringBuilder sb, FlowNode node, List<FlowConnection> outgoing) {
        sb.append("    <block>\n");
        if (node.getQueue() != null && node.getQueue().getQueueName() != null) {
            sb.append("      <prompt>Please hold while we connect you to ").append(escapeXml(node.getQueue().getQueueName())).append(".</prompt>\n");
        } else {
            sb.append("      <prompt>").append(escapeXml(getNodePrompt(node))).append("</prompt>\n");
        }
        for (FlowConnection conn : outgoing) {
            if (conn.getTargetNodeId() != null && !conn.getTargetNodeId().isBlank()) {
                sb.append("      <goto next=\"#").append(escapeXml(conn.getTargetNodeId())).append("\"/>\n");
                break;
            }
        }
        sb.append("    </block>\n");
    }

    private void renderCondition(StringBuilder sb, FlowNode node, List<FlowConnection> outgoing) {
        sb.append("    <block>\n");

        List<FlowConnection> trueConns = outgoing.stream()
                .filter(c -> "true".equalsIgnoreCase(c.getSourcePort()))
                .toList();
        List<FlowConnection> falseConns = outgoing.stream()
                .filter(c -> "false".equalsIgnoreCase(c.getSourcePort()))
                .toList();
        List<FlowConnection> otherConns = outgoing.stream()
                .filter(c -> !"true".equalsIgnoreCase(c.getSourcePort()) && !"false".equalsIgnoreCase(c.getSourcePort()))
                .toList();

        if (node.getCondition() != null && node.getCondition().getBranches() != null && !node.getCondition().getBranches().isEmpty()) {
            for (int i = 0; i < node.getCondition().getBranches().size(); i++) {
                FlowConditionBranch branch = node.getCondition().getBranches().get(i);
                String cond = branch.getCondition() != null ? branch.getCondition() : "true";
                String target = branch.getTargetNodeId();
                if (i == 0) {
                    sb.append("      <if cond=\"").append(escapeXml(cond)).append("\">\n");
                } else {
                    sb.append("      <elseif cond=\"").append(escapeXml(cond)).append("\">\n");
                }
                if (target != null && !target.isBlank()) {
                    sb.append("        <goto next=\"#").append(escapeXml(target)).append("\"/>\n");
                }
                sb.append("      </elseif>\n");
            }
            if (!falseConns.isEmpty() || node.getCondition().getFalseTargetNodeId() != null) {
                sb.append("      <else>\n");
                if (node.getCondition().getFalseTargetNodeId() != null) {
                    sb.append("        <goto next=\"#").append(escapeXml(node.getCondition().getFalseTargetNodeId())).append("\"/>\n");
                } else {
                    for (FlowConnection conn : falseConns) {
                        if (conn.getTargetNodeId() != null && !conn.getTargetNodeId().isBlank()) {
                            sb.append("        <goto next=\"#").append(escapeXml(conn.getTargetNodeId())).append("\"/>\n");
                            break;
                        }
                    }
                }
                sb.append("      </else>\n");
            }
            sb.append("    </if>\n");
        } else {
            String expr = node.getCondition() != null ? node.getCondition().getExpression() : "true";
            sb.append("      <if cond=\"").append(escapeXml(expr)).append("\">\n");
            for (FlowConnection conn : trueConns) {
                if (conn.getTargetNodeId() != null && !conn.getTargetNodeId().isBlank()) {
                    sb.append("        <goto next=\"#").append(escapeXml(conn.getTargetNodeId())).append("\"/>\n");
                    break;
                }
            }
            sb.append("      </if>\n");
            if (!falseConns.isEmpty()) {
                sb.append("      <else>\n");
                for (FlowConnection conn : falseConns) {
                    if (conn.getTargetNodeId() != null && !conn.getTargetNodeId().isBlank()) {
                        sb.append("        <goto next=\"#").append(escapeXml(conn.getTargetNodeId())).append("\"/>\n");
                        break;
                    }
                }
                sb.append("      </else>\n");
            }
        }

        sb.append("    </block>\n");
    }

    private void renderBusinessHours(StringBuilder sb, FlowNode node, List<FlowConnection> outgoing) {
        sb.append("    <block>\n");
        List<FlowConnection> openConns = outgoing.stream()
                .filter(c -> "open".equalsIgnoreCase(c.getSourcePort()))
                .toList();
        List<FlowConnection> closedConns = outgoing.stream()
                .filter(c -> "closed".equalsIgnoreCase(c.getSourcePort()))
                .toList();

        if (node.getBusinessHours() != null && node.getBusinessHours().getOpenTime() != null && node.getBusinessHours().getCloseTime() != null) {
            sb.append("      <if cond=\"isBusinessHours('").append(escapeXml(node.getBusinessHours().getOpenTime()))
              .append("','").append(escapeXml(node.getBusinessHours().getCloseTime())).append("')\">\n");
        } else {
            sb.append("      <if cond=\"isBusinessHours()\">\n");
        }

        if (!openConns.isEmpty()) {
            sb.append("        <goto next=\"#").append(escapeXml(openConns.get(0).getTargetNodeId())).append("\"/>\n");
        } else if (node.getBusinessHours() != null && node.getBusinessHours().getOpenTargetNodeId() != null) {
            sb.append("        <goto next=\"#").append(escapeXml(node.getBusinessHours().getOpenTargetNodeId())).append("\"/>\n");
        }

        sb.append("      </if>\n");
        sb.append("      <else>\n");

        if (!closedConns.isEmpty()) {
            sb.append("        <goto next=\"#").append(escapeXml(closedConns.get(0).getTargetNodeId())).append("\"/>\n");
        } else if (node.getBusinessHours() != null && node.getBusinessHours().getClosedTargetNodeId() != null) {
            sb.append("        <goto next=\"#").append(escapeXml(node.getBusinessHours().getClosedTargetNodeId())).append("\"/>\n");
        }

        sb.append("      </else>\n");
        sb.append("    </block>\n");
    }

    private void renderHoliday(StringBuilder sb, FlowNode node, List<FlowConnection> outgoing) {
        sb.append("    <block>\n");
        sb.append("      <if cond=\"isHoliday()\">\n");
        for (FlowConnection conn : outgoing) {
            if (conn.getTargetNodeId() != null && !conn.getTargetNodeId().isBlank()) {
                sb.append("        <goto next=\"#").append(escapeXml(conn.getTargetNodeId())).append("\"/>\n");
                break;
            }
        }
        sb.append("      </if>\n");
        sb.append("      <else>\n");
        sb.append("        <goto next=\"#normal_day\"/>\n");
        sb.append("      </else>\n");
        sb.append("    </block>\n");
    }

    private void renderEnd(StringBuilder sb, FlowNode node, List<FlowConnection> outgoing) {
        sb.append("    <block>\n");
        sb.append("      <prompt>").append(escapeXml(getNodePrompt(node))).append("</prompt>\n");
        sb.append("      <disconnect/>\n");
        sb.append("    </block>\n");
    }

    private void renderDisconnect(StringBuilder sb, FlowNode node, List<FlowConnection> outgoing) {
        sb.append("    <block>\n");
        sb.append("      <disconnect/>\n");
        sb.append("    </block>\n");
    }

    private void renderRecording(StringBuilder sb, FlowNode node, List<FlowConnection> outgoing) {
        sb.append("    <record name=\"").append(escapeXml(node.getId())).append("\"");
        if (node.getRecording() != null) {
            if (node.getRecording().getMaxDurationSeconds() > 0) {
                sb.append(" maxlength=\"").append(node.getRecording().getMaxDurationSeconds()).append("\"");
            }
            if (node.getRecording().isBeep()) {
                sb.append(" beep=\"true\"");
            }
            sb.append(" dtmf=\"true\"");
        }
        sb.append(">\n");
        sb.append("      <prompt>").append(escapeXml(getNodePrompt(node))).append("</prompt>\n");
        sb.append("    </record>\n");
    }

    private void renderServiceBlock(StringBuilder sb, FlowNode node, List<FlowConnection> outgoing, String serviceType) {
        sb.append("    <block>\n");
        sb.append("      <prompt>").append(escapeXml(getNodePrompt(node))).append("</prompt>\n");
        for (FlowConnection conn : outgoing) {
            if (conn.getTargetNodeId() != null && !conn.getTargetNodeId().isBlank()) {
                sb.append("      <goto next=\"#").append(escapeXml(conn.getTargetNodeId())).append("\"/>\n");
                break;
            }
        }
        sb.append("    </block>\n");
    }

    private void renderAi(StringBuilder sb, FlowNode node, List<FlowConnection> outgoing) {
        sb.append("    <block>\n");
        if (node.getAi() != null && node.getAi().isRoutingMode()) {
            sb.append("      <prompt>").append(escapeXml(getNodePrompt(node))).append("</prompt>\n");
            sb.append("      <ai role=\"").append(escapeXml(node.getAi().getRole())).append("\"");
            
            // Build options string
            StringBuilder optSb = new StringBuilder();
            Map<String, String> opts = node.getAi().getRoutingOptions();
            if (opts != null && !opts.isEmpty()) {
                for (Map.Entry<String, String> entry : opts.entrySet()) {
                    if (optSb.length() > 0) optSb.append(",");
                    optSb.append(entry.getKey()).append(":").append(entry.getValue());
                }
            } else {
                for (FlowConnection conn : outgoing) {
                    if (!"nomatch".equals(conn.getSourcePort()) && conn.getTargetNodeId() != null && !conn.getTargetNodeId().isBlank()) {
                        if (optSb.length() > 0) optSb.append(",");
                        optSb.append(conn.getSourcePort()).append(":").append(conn.getTargetNodeId());
                    }
                }
            }
            sb.append(" options=\"").append(escapeXml(optSb.toString())).append("\"/>\n");
            
            // Render nomatch/fallback goto if present
            for (FlowConnection conn : outgoing) {
                if ("nomatch".equals(conn.getSourcePort()) && conn.getTargetNodeId() != null && !conn.getTargetNodeId().isBlank()) {
                    sb.append("      <goto next=\"#").append(escapeXml(conn.getTargetNodeId())).append("\"/>\n");
                }
            }
        } else if (node.getAi() != null && node.getAi().getAgentId() != null && !node.getAi().getAgentId().isBlank()) {
            sb.append("      <subdialog src=\"ai://").append(escapeXml(node.getAi().getAgentId())).append("\"");
            if (node.getAi().getMaxTurns() > 0) {
                sb.append(" maxturns=\"").append(node.getAi().getMaxTurns()).append("\"");
            }
            sb.append(">\n");
            sb.append("        <prompt>").append(escapeXml(getNodePrompt(node))).append("</prompt>\n");
            sb.append("      </subdialog>\n");
            for (FlowConnection conn : outgoing) {
                if (conn.getTargetNodeId() != null && !conn.getTargetNodeId().isBlank()) {
                    sb.append("      <goto next=\"#").append(escapeXml(conn.getTargetNodeId())).append("\"/>\n");
                    break;
                }
            }
        } else {
            sb.append("      <prompt>").append(escapeXml(getNodePrompt(node))).append("</prompt>\n");
            for (FlowConnection conn : outgoing) {
                if (conn.getTargetNodeId() != null && !conn.getTargetNodeId().isBlank()) {
                    sb.append("      <goto next=\"#").append(escapeXml(conn.getTargetNodeId())).append("\"/>\n");
                    break;
                }
            }
        }
        sb.append("    </block>\n");
    }

    private void renderVoicemail(StringBuilder sb, FlowNode node, List<FlowConnection> outgoing) {
        sb.append("    <block>\n");
        sb.append("      <prompt>").append(escapeXml(getNodePrompt(node))).append("</prompt>\n");
        sb.append("      <record name=\"voicemail\" maxlength=\"120\" beep=\"true\" dtmf=\"true\">\n");
        sb.append("        <prompt>Please leave a message after the beep.</prompt>\n");
        sb.append("      </record>\n");
        for (FlowConnection conn : outgoing) {
            if (conn.getTargetNodeId() != null && !conn.getTargetNodeId().isBlank()) {
                sb.append("      <goto next=\"#").append(escapeXml(conn.getTargetNodeId())).append("\"/>\n");
                break;
            }
        }
        sb.append("    </block>\n");
    }

    private void renderDefault(StringBuilder sb, FlowNode node, List<FlowConnection> outgoing) {
        renderPromptAndGoto(sb, node, outgoing, true);
    }

    private String getNodePrompt(FlowNode node) {
        if (node.getPrompt() != null && node.getPrompt().getText() != null && !node.getPrompt().getText().isBlank()) {
            return node.getPrompt().getText();
        }
        if (node.getTitle() != null && !node.getTitle().isBlank()) {
            return node.getTitle();
        }
        return "";
    }

    private String extractDtmfFromKey(String key) {
        if (key == null || key.isBlank()) {
            return "1";
        }
        String trimmed = key.trim();
        if (trimmed.matches("\\d")) {
            return trimmed;
        }
        if (trimmed.startsWith("key") && trimmed.length() > 3) {
            String digit = trimmed.substring(3);
            if (digit.matches("\\d")) {
                return digit;
            }
        }
        return "1";
    }

    private String minimalVxml() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <vxml version="2.1" xmlns="http://www.w3.org/2001/vxml">
                  <form id="start">
                    <block>
                      <prompt>Welcome to our service.</prompt>
                      <disconnect/>
                    </block>
                  </form>
                </vxml>
                """;
    }

    private String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> sb.append("&");
                case '<' -> sb.append("<");
                case '>' -> sb.append(">");
                case '"' -> sb.append("\"");
                case '\'' -> sb.append("'");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
