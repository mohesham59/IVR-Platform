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
        
        if (node.getType() == FlowNodeType.MENU) {
            sb.append("  <menu id=\"").append(escapeXml(node.getId())).append("\">\n");
        } else {
            sb.append("  <form id=\"").append(escapeXml(node.getId())).append("\">\n");
        }

        List<FlowConnection> nodeOutgoing = outgoing.getOrDefault(node.getId(), List.of());

        switch (node.getType()) {
            case START -> renderStart(sb, node, nodeOutgoing);
            case PROMPT -> renderPrompt(sb, node, nodeOutgoing);
            case MENU -> {
                renderBilingualPrompts(sb, node, true, "Please select an option.");
                renderMenuOptions(sb, node, nodeOutgoing);
                renderFallbackHandlers(sb, nodeOutgoing);
            }
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
            case VARIABLE -> renderVariable(sb, node, nodeOutgoing);
            default -> renderDefault(sb, node, nodeOutgoing);
        }

        if (node.getType() == FlowNodeType.MENU) {
            sb.append("  </menu>\n");
        } else {
            sb.append("  </form>\n");
        }
    }

    private void renderBilingualPrompts(StringBuilder sb, FlowNode node, boolean bargein, String fallbackEn) {
        String b = bargein ? " bargein=\"true\"" : " bargein=\"false\"";
        boolean hasEn = (node.getPromptEn() != null && !node.getPromptEn().isBlank()) || (node.getAudioEn() != null && !node.getAudioEn().isBlank());
        boolean hasAr = (node.getPromptAr() != null && !node.getPromptAr().isBlank()) || (node.getAudioAr() != null && !node.getAudioAr().isBlank());

        if (!hasEn && !hasAr) {
            String text = getNodePrompt(node);
            if (text == null || text.isBlank()) {
                text = fallbackEn != null ? fallbackEn : (node.getTitle() != null ? node.getTitle() : "");
            }
            if (text.endsWith(".wav") || text.endsWith(".mp3")) {
                sb.append("      <prompt").append(b).append(">\n");
                sb.append("        <audio src=\"").append(escapeXml(text)).append("\">").append(escapeXml(node.getTitle() != null ? node.getTitle() : "")).append("</audio>\n");
                sb.append("      </prompt>\n");
            } else {
                sb.append("      <prompt").append(b).append(">").append(escapeXml(text)).append("</prompt>\n");
            }
            return;
        }

        if (hasEn) {
            sb.append("      <prompt").append(b).append(" xml:lang=\"en\">\n");
            if (node.getAudioEn() != null && !node.getAudioEn().isBlank()) {
                sb.append("        <audio src=\"").append(escapeXml(node.getAudioEn())).append("\">").append(escapeXml(node.getPromptEn() != null ? node.getPromptEn() : "")).append("</audio>\n");
            } else {
                sb.append("        ").append(escapeXml(node.getPromptEn() != null ? node.getPromptEn() : "")).append("\n");
            }
            sb.append("      </prompt>\n");
        }
        if (hasAr) {
            sb.append("      <prompt").append(b).append(" xml:lang=\"ar\">\n");
            if (node.getAudioAr() != null && !node.getAudioAr().isBlank()) {
                sb.append("        <audio src=\"").append(escapeXml(node.getAudioAr())).append("\">").append(escapeXml(node.getPromptAr() != null ? node.getPromptAr() : "")).append("</audio>\n");
            } else {
                sb.append("        ").append(escapeXml(node.getPromptAr() != null ? node.getPromptAr() : "")).append("\n");
            }
            sb.append("      </prompt>\n");
        }
    }

    private void renderStart(StringBuilder sb, FlowNode node, List<FlowConnection> outgoing) {
        renderPromptAndGoto(sb, node, outgoing, true);
    }

    private void renderPrompt(StringBuilder sb, FlowNode node, List<FlowConnection> outgoing) {
        renderPromptAndGoto(sb, node, outgoing, true);
    }

    private void renderPromptAndGoto(StringBuilder sb, FlowNode node, List<FlowConnection> outgoing, boolean isBlock) {
        if (isBlock) {
            sb.append("    <block>\n");
        }
        renderBilingualPrompts(sb, node, false, null);
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

    private void renderMenuOptions(StringBuilder sb, FlowNode node, List<FlowConnection> outgoing) {
        String promptText = getNodePrompt(node);

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
                    target = outgoing.stream()
                            .filter(conn -> Objects.equals(conn.getSourcePort(), choice.getKey()))
                            .findFirst()
                            .map(FlowConnection::getTargetNodeId)
                            .orElse(null);
                }
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
                String sourcePort = conn.getSourcePort();
                if (sourcePort != null && (sourcePort.equalsIgnoreCase("timeout") || sourcePort.equalsIgnoreCase("error") || sourcePort.equalsIgnoreCase("invalid") || sourcePort.equalsIgnoreCase("nomatch"))) {
                    continue;
                }
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
        sb.append("    <field name=\"").append(escapeXml(node.getId())).append("\" type=\"digits\"");
        if (node.getMaxDigits() != null && node.getMaxDigits() > 0) {
            sb.append(" maxlen=\"").append(node.getMaxDigits()).append("\"");
        } else if (node.getInput() != null && node.getInput().getDigits() > 0) {
            sb.append(" maxlen=\"").append(node.getInput().getDigits()).append("\"");
        }
        sb.append(">\n");

        renderBilingualPrompts(sb, node, true, "Please enter your selection.");

        List<FlowConnection> successConns = outgoing.stream()
                .filter(c -> "success".equalsIgnoreCase(c.getSourcePort()) || "out".equalsIgnoreCase(c.getSourcePort()))
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

        renderFallbackHandlers(sb, outgoing);

        sb.append("    </field>\n");
    }

    private void renderFallbackHandlers(StringBuilder sb, List<FlowConnection> outgoing) {
        List<FlowConnection> timeoutConns = outgoing.stream()
                .filter(c -> "timeout".equalsIgnoreCase(c.getSourcePort()))
                .toList();
        List<FlowConnection> errorConns = outgoing.stream()
                .filter(c -> "error".equalsIgnoreCase(c.getSourcePort()) || "invalid".equalsIgnoreCase(c.getSourcePort()) || "nomatch".equalsIgnoreCase(c.getSourcePort()))
                .toList();

        sb.append("      <noinput>\n");
        sb.append("        <prompt xml:lang=\"en\">We did not receive any input. Please try again.</prompt>\n");
        sb.append("        <prompt xml:lang=\"ar\">لم نتلق أي إدخال. يرجى المحاولة مرة أخرى.</prompt>\n");
        if (!timeoutConns.isEmpty()) {
            boolean gotoAdded = false;
            for (FlowConnection conn : timeoutConns) {
                if (conn.getTargetNodeId() != null && !conn.getTargetNodeId().isBlank()) {
                    sb.append("        <goto next=\"#").append(escapeXml(conn.getTargetNodeId())).append("\"/>\n");
                    gotoAdded = true;
                    break;
                }
            }
            if (!gotoAdded) {
                sb.append("        <reprompt/>\n");
            }
        } else {
            sb.append("        <reprompt/>\n");
        }
        sb.append("      </noinput>\n");

        sb.append("      <nomatch>\n");
        sb.append("        <prompt xml:lang=\"en\">Invalid option. Please try again.</prompt>\n");
        sb.append("        <prompt xml:lang=\"ar\">خيار غير صالح. يرجى المحاولة مرة أخرى.</prompt>\n");
        if (!errorConns.isEmpty()) {
            boolean gotoAdded = false;
            for (FlowConnection conn : errorConns) {
                if (conn.getTargetNodeId() != null && !conn.getTargetNodeId().isBlank()) {
                    sb.append("        <goto next=\"#").append(escapeXml(conn.getTargetNodeId())).append("\"/>\n");
                    gotoAdded = true;
                    break;
                }
            }
            if (!gotoAdded) {
                sb.append("        <reprompt/>\n");
            }
        } else {
            sb.append("        <reprompt/>\n");
        }
        sb.append("      </nomatch>\n");
    }

    private void renderTransfer(StringBuilder sb, FlowNode node, List<FlowConnection> outgoing) {
        sb.append("    <block>\n");
        renderBilingualPrompts(sb, node, false, "Please hold while I transfer your call.");
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
            renderBilingualPrompts(sb, node, false, "Please hold while we connect you to " + node.getQueue().getQueueName() + ".");
        } else {
            renderBilingualPrompts(sb, node, false, null);
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
        renderBilingualPrompts(sb, node, false, "Goodbye.");
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
                sb.append(" maxtime=\"").append(node.getRecording().getMaxDurationSeconds()).append("\"");
            }
            if (node.getRecording().isBeep()) {
                sb.append(" beep=\"true\"");
            }
            sb.append(" dtmf=\"true\"");
        }
        sb.append(">\n");
        renderBilingualPrompts(sb, node, true, "Please record your message after the beep.");
        sb.append("    </record>\n");
    }

    private void renderServiceBlock(StringBuilder sb, FlowNode node, List<FlowConnection> outgoing, String serviceType) {
        String url = node.getSubtitle() != null && !node.getSubtitle().isBlank() ? node.getSubtitle() : "https://api.example.com/endpoint";
        String varName = serviceType.toLowerCase() + "_result_" + node.getId().replaceAll("[^a-zA-Z0-9]", "_");
        
        sb.append("    <block>\n");
        sb.append("      <api url=\"").append(escapeXml(url)).append("\" var=\"").append(varName).append("\" saveResultAs=\"").append(varName).append("\"/>\n");
        sb.append("    </block>\n");
        sb.append("    <block>\n");

        FlowConnection successEdge = outgoing.stream().filter(c -> "success".equalsIgnoreCase(c.getSourcePort()) || "found".equalsIgnoreCase(c.getSourcePort())).findFirst().orElse(null);
        FlowConnection errorEdge = outgoing.stream().filter(c -> "error".equalsIgnoreCase(c.getSourcePort()) || "notfound".equalsIgnoreCase(c.getSourcePort())).findFirst().orElse(null);

        if (successEdge != null || errorEdge != null) {
            sb.append("      <if cond=\"").append(varName).append(" != null\">\n");
            if (successEdge != null) {
                sb.append("        <goto next=\"#").append(escapeXml(successEdge.getTargetNodeId())).append("\"/>\n");
            }
            sb.append("      <else/>\n");
            if (errorEdge != null) {
                sb.append("        <goto next=\"#").append(escapeXml(errorEdge.getTargetNodeId())).append("\"/>\n");
            }
            sb.append("      </if>\n");
        } else {
            for (FlowConnection conn : outgoing) {
                if (conn.getTargetNodeId() != null && !conn.getTargetNodeId().isBlank()) {
                    sb.append("      <goto next=\"#").append(escapeXml(conn.getTargetNodeId())).append("\"/>\n");
                    break;
                }
            }
        }
        sb.append("    </block>\n");
    }

    private void renderAi(StringBuilder sb, FlowNode node, List<FlowConnection> outgoing) {
        sb.append("    <block>\n");
        
        boolean isRouting = (node.getAiRole() != null && !node.getAiRole().isBlank()) || 
                            (node.getAi() != null && node.getAi().isRoutingMode());
                            
        if (isRouting) {
            String role = node.getAiRole() != null && !node.getAiRole().isBlank() ? node.getAiRole() : (node.getAi() != null ? node.getAi().getRole() : "You are a polite assistant.");
            sb.append("      <ai role=\"").append(escapeXml(role)).append("\"");
            
            // Build options string
            StringBuilder optSb = new StringBuilder();
            Map<String, String> opts = node.getAi() != null ? node.getAi().getRoutingOptions() : null;
            if (opts != null && !opts.isEmpty()) {
                for (Map.Entry<String, String> entry : opts.entrySet()) {
                    if (optSb.length() > 0) optSb.append(",");
                    optSb.append(entry.getKey()).append(":").append(entry.getValue());
                }
            } else {
                for (FlowConnection conn : outgoing) {
                    if (!"nomatch".equals(conn.getSourcePort()) && conn.getTargetNodeId() != null && !conn.getTargetNodeId().isBlank()) {
                        if (optSb.length() > 0) optSb.append(",");
                        // Use connection label if present, else fallback to targetId
                        String intentLabel = conn.getLabel() != null && !conn.getLabel().isBlank() ? conn.getLabel() : conn.getTargetNodeId();
                        optSb.append(escapeXml(intentLabel.replace(":", ""))).append(":").append(conn.getTargetNodeId());
                    }
                }
            }
            sb.append(" options=\"").append(escapeXml(optSb.toString())).append("\">\n");
            
            renderBilingualPrompts(sb, node, true, "How can I help you?");
            
            sb.append("      </ai>\n");
            
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
            renderBilingualPrompts(sb, node, true, null);
            sb.append("      </subdialog>\n");
            for (FlowConnection conn : outgoing) {
                if (conn.getTargetNodeId() != null && !conn.getTargetNodeId().isBlank()) {
                    sb.append("      <goto next=\"#").append(escapeXml(conn.getTargetNodeId())).append("\"/>\n");
                    break;
                }
            }
        } else {
            renderBilingualPrompts(sb, node, true, null);
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
        renderBilingualPrompts(sb, node, false, "Please leave a message after the beep.");
        sb.append("      <record name=\"voicemail\" maxlength=\"120\" beep=\"true\" dtmf=\"true\">\n");
        sb.append("      </record>\n");
        for (FlowConnection conn : outgoing) {
            if (conn.getTargetNodeId() != null && !conn.getTargetNodeId().isBlank()) {
                sb.append("      <goto next=\"#").append(escapeXml(conn.getTargetNodeId())).append("\"/>\n");
                break;
            }
        }
        sb.append("    </block>\n");
    }

    private void renderVariable(StringBuilder sb, FlowNode node, List<FlowConnection> outgoing) {
        String varName = node.getId();
        String varValue = "''";
        if (node.getSubtitle() != null && !node.getSubtitle().isBlank()) {
            if (node.getSubtitle().contains("=")) {
                String[] parts = node.getSubtitle().split("=", 2);
                varName = parts[0].trim();
                if (varName.toLowerCase().startsWith("set ")) {
                    varName = varName.substring(4).trim();
                }
                varValue = parts[1].trim();
            } else {
                varName = node.getSubtitle().trim();
            }
        } else if (node.getVariableName() != null && !node.getVariableName().isBlank()) {
            varName = node.getVariableName();
            varValue = node.getVariableValue() != null ? node.getVariableValue() : "''";
        }
        sb.append("    <block>\n");
        sb.append("      <assign name=\"").append(escapeXml(varName)).append("\" expr=\"'").append(escapeXml(varValue.replaceAll("^['\"]|['\"]$", ""))).append("'\"/>\n");
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
