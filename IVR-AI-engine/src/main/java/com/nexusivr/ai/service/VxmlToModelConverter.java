package com.nexusivr.ai.service;

import com.nexusivr.ai.model.flow.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts VoiceXML 2.1 directly into the Internal Flow Model.
 * <p>
 * This replaces the old VxmlParser + VxmlToFlowConverter pipeline.
 * VoiceXML is parsed into a rich object model that validation and
 * auto-repair can operate on directly, without ever converting to
 * Builder JSON.
 */
public class VxmlToModelConverter {

    private static final String DEFAULT_NAMESPACE = "http://www.w3.org/2001/vxml";

    private static final Logger logger = LoggerFactory.getLogger(VxmlToModelConverter.class);

    public FlowModel convert(String vxmlContent) throws VxmlParseException {
        if (vxmlContent == null || vxmlContent.isBlank()) {
            throw new VxmlParseException("VXML content is empty");
        }

        String sanitized;
        try {
            sanitized = LlmResponseNormalizer.normalize(vxmlContent);
        } catch (LlmResponseNormalizationException e) {
            throw new VxmlParseException("Failed to normalize VXML: " + e.getMessage(), e);
        }

        sanitized = sanitizeMalformedIfElse(sanitized);

        logger.info("[VxmlToModelConverter] Parser Stage: VoiceXML → FlowModel. Input length={} chars.", vxmlContent.length());

        logger.info("[VxmlToModelConverter] Parser Stage: VoiceXML → FlowModel. First 20 chars (codes): {}",
                formatFirstChars(sanitized));

        Document doc;
        try {
            doc = parseXml(sanitized);
        } catch (Exception e) {
            logger.warn("[VxmlToModelConverter] Parser Stage: VoiceXML → FlowModel. Status: FAILED. Reason: {}.", e.getMessage());
            throw new VxmlParseException("Failed to parse VXML XML: " + e.getMessage(), e);
        }

        Element root = doc.getDocumentElement();
        if (!"vxml".equalsIgnoreCase(root.getTagName())) {
            logger.warn("[VxmlToModelConverter] Parser Stage: VoiceXML → FlowModel. Status: FAILED. Reason: Root element must be <vxml>, got: {}.", root.getTagName());
            throw new VxmlParseException("Root element must be <vxml>, got: " + root.getTagName());
        }

        FlowModel model = new FlowModel();
        model.setVoicexmlVersion(root.getAttribute("version"));
        if (model.getVoicexmlVersion().isBlank()) {
            model.setVoicexmlVersion("2.1");
        }

        NodeList metas = root.getElementsByTagName("meta");
        for (int i = 0; i < metas.getLength(); i++) {
            Element metaEl = (Element) metas.item(i);
            if ("flow-name".equalsIgnoreCase(metaEl.getAttribute("name"))) {
                model.setName(metaEl.getAttribute("content"));
            }
        }

        List<Element> dialogNodes = new ArrayList<>();
        NodeList childNodes = root.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node n = childNodes.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE) {
                String tagName = n.getNodeName();
                if ("form".equalsIgnoreCase(tagName) || "menu".equalsIgnoreCase(tagName)) {
                    dialogNodes.add((Element) n);
                }
            }
        }
        if (dialogNodes.isEmpty()) {
            logger.warn("[VxmlToModelConverter] Parser Stage: VoiceXML → FlowModel. Status: FAILED. Reason: VXML must contain at least one <form> or <menu>.");
            throw new VxmlParseException("VXML must contain at least one <form> or <menu>");
        }

        Map<String, FlowNode> nodeMap = new LinkedHashMap<>();

        for (int i = 0; i < dialogNodes.size(); i++) {
            Element formEl = dialogNodes.get(i);
            FlowNode node = parseForm(formEl);
            if (node != null) {
                if (i == 0 && node.getType() == FlowNodeType.PROMPT) {
                    node.setType(FlowNodeType.START);
                    node.setTitle("Start");
                    if (node.getPrompt() != null && node.getPrompt().getText() != null && !node.getPrompt().getText().isBlank()) {
                        node.setSubtitle(node.getPrompt().getDisplayText());
                    } else {
                        node.setSubtitle("Entry Point");
                    }
                }
                model.addNode(node);
                nodeMap.put(node.getId(), node);
            }
        }

        for (int i = 0; i < dialogNodes.size(); i++) {
            Element formEl = dialogNodes.get(i);
            String formId = formEl.getAttribute("id");
            if (formId == null || formId.isBlank()) {
                formId = nodeMap.keySet().toArray(new String[0])[i];
            }
            FlowNode sourceNode = nodeMap.get(formId);
            if (sourceNode == null) continue;

            parseConnections(formEl, sourceNode, nodeMap, model);
        }

        logger.info("[VxmlToModelConverter] Parser Stage: VoiceXML → FlowModel. Status: SUCCESS. Nodes={}, Connections={}.",
                model.getNodes().size(), model.getConnections().size());
        return model;
    }

    private FlowNode parseForm(Element formEl) {
        String id = formEl.getAttribute("id");
        if (id == null || id.isBlank()) {
            id = "form_" + UUID.randomUUID().toString().substring(0, 8);
        }

        FlowNodeType nodeType = determineNodeType(formEl);
        if (nodeType == null) {
            nodeType = FlowNodeType.PROMPT;
        }

        String commentTitle = findPrecedingCommentTitle(formEl);
        String title = (commentTitle != null && !commentTitle.isBlank()) ? commentTitle : formatTitle(id, nodeType);
        FlowNode node = new FlowNode(id, nodeType, title);
        node.setVoicexmlRef(id);

        if ("menu".equalsIgnoreCase(formEl.getTagName())) {
            node.setMenu(parseMenu(formEl));
            String menuPrompt = parseMenuPrompt(formEl);
            if (menuPrompt != null && !menuPrompt.isBlank()) {
                if (node.getPrompt() == null) {
                    node.setPrompt(new FlowPrompt());
                }
                node.getPrompt().setText(menuPrompt);
            }
        }

        NodeList children = formEl.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node nodeEl = children.item(i);
            if (nodeEl.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element el = (Element) nodeEl;
            String tag = el.getTagName().toLowerCase(Locale.ROOT);

            switch (tag) {
                case "block" -> node.setPrompt(parseBlock(el));
                case "field" -> node.setInput(parseField(el));
                case "menu" -> {
                    node.setMenu(parseMenu(el));
                    String menuPrompt = parseMenuPrompt(el);
                    if (menuPrompt != null && !menuPrompt.isBlank()) {
                        if (node.getPrompt() == null) {
                            node.setPrompt(new FlowPrompt());
                        }
                        node.getPrompt().setText(menuPrompt);
                    }
                }
                case "transfer" -> node.setTransfer(parseTransfer(el));
                case "prompt" -> {
                    if (node.getPrompt() == null) {
                        node.setPrompt(new FlowPrompt());
                    }
                    node.getPrompt().setText(parsePromptText(el));
                }
                case "disconnect", "hangup" -> node.setType(FlowNodeType.END);
                case "if" -> node.setCondition(parseIf(el));
                case "subdialog" -> node.setAi(new FlowAi("subdialog", ""));
                case "ai" -> {
                    FlowAi ai = new FlowAi();
                    ai.setRole(el.getAttribute("role"));
                    String optionsStr = el.getAttribute("options");
                    if (optionsStr != null && !optionsStr.isBlank()) {
                        LinkedHashMap<String, String> routingOptions = new LinkedHashMap<>();
                        for (String opt : optionsStr.split(",")) {
                            String[] parts = opt.split(":");
                            if (parts.length == 2) {
                                routingOptions.put(parts[0].trim(), parts[1].trim());
                            }
                        }
                        ai.setRoutingOptions(routingOptions);
                    }
                    node.setAi(ai);
                }
                default -> {
                }
            }
        }

        // Fallback checks for nested tags anywhere in the form
        if (node.getTransfer() == null) {
            NodeList transfers = formEl.getElementsByTagName("transfer");
            if (transfers.getLength() > 0) {
                node.setTransfer(parseTransfer((Element) transfers.item(0)));
            }
        }
        if (node.getInput() == null) {
            NodeList fields = formEl.getElementsByTagName("field");
            if (fields.getLength() > 0) {
                node.setInput(parseField((Element) fields.item(0)));
            }
        }
        if (node.getMenu() == null) {
            NodeList menus = formEl.getElementsByTagName("menu");
            if (menus.getLength() > 0) {
                Element menuEl = (Element) menus.item(0);
                node.setMenu(parseMenu(menuEl));
                String menuPrompt = parseMenuPrompt(menuEl);
                if (menuPrompt != null && !menuPrompt.isBlank()) {
                    if (node.getPrompt() == null) {
                        node.setPrompt(new FlowPrompt());
                    }
                    node.getPrompt().setText(menuPrompt);
                }
            }
        }

        if (node.getAi() == null) {
            NodeList ais = formEl.getElementsByTagName("ai");
            if (ais.getLength() > 0) {
                Element aiEl = (Element) ais.item(0);
                FlowAi ai = new FlowAi();
                ai.setRole(aiEl.getAttribute("role"));
                String optionsStr = aiEl.getAttribute("options");
                if (optionsStr != null && !optionsStr.isBlank()) {
                    LinkedHashMap<String, String> routingOptions = new LinkedHashMap<>();
                    for (String opt : optionsStr.split(",")) {
                        String[] parts = opt.split(":");
                        if (parts.length == 2) {
                            routingOptions.put(parts[0].trim(), parts[1].trim());
                        }
                    }
                    ai.setRoutingOptions(routingOptions);
                }
                node.setAi(ai);
            } else {
                NodeList subdialogs = formEl.getElementsByTagName("subdialog");
                if (subdialogs.getLength() > 0) {
                    node.setAi(new FlowAi("subdialog", ""));
                }
            }
        }

        if (node.getType() == FlowNodeType.START) {
            node.setTitle("Start");
            if (node.getPrompt() != null && node.getPrompt().getText() != null && !node.getPrompt().getText().isBlank()) {
                node.setSubtitle(node.getPrompt().getDisplayText());
            } else {
                node.setSubtitle("Entry Point");
            }
        } else if (node.getType() == FlowNodeType.END) {
            String formId = node.getId();
            if (formId == null || formId.isBlank() || "end".equalsIgnoreCase(formId) || "disconnect".equalsIgnoreCase(formId)) {
                node.setTitle("End Call");
                node.setSubtitle("Hang up");
            } else {
                String[] words = formId.split("[_-]");
                StringBuilder sb = new StringBuilder();
                for (String word : words) {
                    if (!word.isEmpty()) {
                        if (sb.length() > 0) sb.append(" ");
                        if ("sim".equalsIgnoreCase(word)) {
                            sb.append("SIM");
                        } else {
                            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
                        }
                    }
                }
                node.setTitle(sb.toString());
                node.setSubtitle("Hang up");
            }
        } else if (node.getPrompt() != null && node.getPrompt().getText() != null && !node.getPrompt().getText().isBlank()) {
            node.setSubtitle(node.getPrompt().getDisplayText());
        }

        return node;
    }

    private FlowPrompt parseBlock(Element blockEl) {
        FlowPrompt prompt = new FlowPrompt();
        StringBuilder text = new StringBuilder();
        NodeList children = blockEl.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.TEXT_NODE) {
                text.append(node.getTextContent().trim());
            } else if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element el = (Element) node;
                if ("prompt".equalsIgnoreCase(el.getTagName())) {
                    String promptText = parsePromptText(el);
                    if (!promptText.isBlank()) {
                        text.append(promptText);
                    }
                } else if ("value".equalsIgnoreCase(el.getTagName()) && el.hasAttribute("expr")) {
                    text.append("${").append(el.getAttribute("expr")).append("}");
                }
            }
        }
        prompt.setText(text.toString().trim());
        return prompt;
    }

    private String parsePromptText(Element promptEl) {
        StringBuilder text = new StringBuilder();
        NodeList children = promptEl.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.TEXT_NODE) {
                text.append(node.getTextContent().trim());
            } else if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element el = (Element) node;
                if ("value".equalsIgnoreCase(el.getTagName()) && el.hasAttribute("expr")) {
                    text.append("${").append(el.getAttribute("expr")).append("}");
                } else if ("audio".equalsIgnoreCase(el.getTagName())) {
                    text.append("[audio: ").append(el.getAttribute("src")).append("]");
                } else {
                    text.append(el.getTextContent().trim());
                }
            }
        }
        return text.toString().trim();
    }

    private FlowMenu parseMenu(Element menuEl) {
        FlowMenu menu = new FlowMenu();

        NodeList children = menuEl.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element el = (Element) node;
            String tag = el.getTagName().toLowerCase(Locale.ROOT);

            switch (tag) {
                case "prompt" -> {
                    // Handled via parseMenuPrompt on parent node
                }
                case "choice" -> {
                    String next = el.getAttribute("next");
                    String accept = el.getAttribute("accept");
                    String dtmf = el.getAttribute("dtmf");
                    String value = el.getAttribute("value");
                    String text = el.getTextContent().trim();

                    String key = deriveKeyFromDtmf(dtmf, value, accept, menu.getChoices().size() + 1);
                    String cleanDtmf = (dtmf != null && !dtmf.isBlank()) ? dtmf : extractDigitFromAccept(accept, key);
                    FlowChoice choice = new FlowChoice(key, text, normalizeTarget(next));
                    choice.setDtmf(cleanDtmf);
                    choice.setAccept(accept);
                    menu.addChoice(choice);
                }
                case "else" -> {
                    String elseNext = el.getAttribute("next");
                    if (elseNext != null && !elseNext.isBlank()) {
                        menu.setElseGoto(normalizeTarget(elseNext));
                    }
                }
                default -> {
                }
            }
        }

        return menu;
    }

    private String parseMenuPrompt(Element menuEl) {
        NodeList children = menuEl.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element el = (Element) node;
                if ("prompt".equalsIgnoreCase(el.getTagName())) {
                    return parsePromptText(el);
                }
            }
        }
        return null;
    }

    private String extractDigitFromAccept(String accept, String fallbackKey) {
        if (accept != null && !accept.isBlank()) {
            String trimmed = accept.trim();
            if (trimmed.startsWith("digits ")) {
                String d = trimmed.substring(7).trim();
                if (d.matches("\\d+")) {
                    return d;
                }
            }
        }
        if (fallbackKey != null && fallbackKey.startsWith("key")) {
            return fallbackKey.substring(3);
        }
        return "1";
    }

    private String deriveKeyFromDtmf(String dtmf, String value, String accept, int fallbackIndex) {
        if (dtmf != null && !dtmf.isBlank()) {
            return "key" + dtmf.trim();
        }
        if (value != null && !value.isBlank()) {
            return "key" + value.trim();
        }
        if (accept != null && !accept.isBlank()) {
            String digits = accept.replaceAll("[^0-9]", "");
            if (!digits.isBlank()) {
                return "key" + digits;
            }
        }
        return "key" + fallbackIndex;
    }

    private String normalizeTarget(String target) {
        if (target == null || target.isBlank()) {
            return null;
        }
        if (target.startsWith("#")) {
            return target.substring(1);
        }
        return target;
    }

    private FlowInput parseField(Element fieldEl) {
        String name = fieldEl.getAttribute("name");
        String type = fieldEl.getAttribute("type");

        int parsedType = 1;
        if (type != null && !type.isBlank()) {
            try {
                parsedType = Integer.parseInt(type);
            } catch (NumberFormatException e) {
                logger.warn("[VxmlToModelConverter] Invalid field type '{}', defaulting to 1.", type);
                parsedType = 1;
            }
        }
        FlowInput input = new FlowInput(name != null && !name.isBlank() ? name : "input", parsedType);

        NodeList children = fieldEl.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element el = (Element) node;
            String tag = el.getTagName().toLowerCase(Locale.ROOT);

            switch (tag) {
                case "prompt" -> {
                    if (input.getName() == null || input.getName().isBlank()) {
                        input.setName(parsePromptText(el));
                    }
                }
                case "grammar" -> {
                    String mode = el.getAttribute("mode");
                    if (mode != null && !mode.isBlank()) {
                        input.setMode(mode);
                    }
                }
                case "filled" -> {
                    // filled handlers are parsed as connections from the input node
                }
                default -> {
                }
            }
        }

        return input;
    }

    private FlowTransfer parseTransfer(Element transferEl) {
        String dest = transferEl.getAttribute("dest");
        return new FlowTransfer(dest != null ? dest : "");
    }

    private FlowCondition parseIf(Element ifEl) {
        String cond = ifEl.getAttribute("cond");
        FlowCondition condition = new FlowCondition(cond);

        NodeList children = ifEl.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element el = (Element) node;
            String tag = el.getTagName().toLowerCase(Locale.ROOT);

            switch (tag) {
                case "goto" -> {
                    String next = el.getAttribute("next");
                    if (next != null && !next.isBlank()) {
                        if (condition.getTrueTargetNodeId() == null) {
                            condition.setTrueTargetNodeId(normalizeTarget(next));
                        } else {
                            condition.setFalseTargetNodeId(normalizeTarget(next));
                        }
                    }
                }
                case "prompt" -> {
                    // prompts inside if are handled by the parent node
                }
                default -> {
                }
            }
        }

        NodeList elseIfs = ifEl.getElementsByTagName("elseif");
        for (int i = 0; i < elseIfs.getLength(); i++) {
            Element elseifEl = (Element) elseIfs.item(i);
            String elseifCond = elseifEl.getAttribute("cond");
            String elseifTarget = null;

            NodeList elseIfChildren = elseifEl.getChildNodes();
            for (int j = 0; j < elseIfChildren.getLength(); j++) {
                Node elseIfNode = elseIfChildren.item(j);
                if (elseIfNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element elseIfEl = (Element) elseIfNode;
                    if ("goto".equalsIgnoreCase(elseIfEl.getTagName())) {
                        elseifTarget = normalizeTarget(elseIfEl.getAttribute("next"));
                    }
                }
            }

            if (elseifCond != null && elseifTarget != null) {
                condition.addBranch(new FlowConditionBranch(elseifCond, elseifTarget));
            }
        }

        NodeList elseNodes = ifEl.getElementsByTagName("else");
        if (elseNodes.getLength() > 0) {
            Element elseEl = (Element) elseNodes.item(0);
            NodeList elseChildren = elseEl.getChildNodes();
            for (int j = 0; j < elseChildren.getLength(); j++) {
                Node elseNode = elseChildren.item(j);
                if (elseNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element elseChildEl = (Element) elseNode;
                    if ("goto".equalsIgnoreCase(elseChildEl.getTagName())) {
                        condition.setFalseTargetNodeId(normalizeTarget(elseChildEl.getAttribute("next")));
                    }
                }
            }
        }

        return condition;
    }

    private void parseConnections(Element formEl, FlowNode sourceNode, Map<String, FlowNode> nodeMap, FlowModel model) {
        String sourceId = sourceNode.getId();

        // <goto> connections
        NodeList gotos = formEl.getElementsByTagName("goto");
        for (int i = 0; i < gotos.getLength(); i++) {
            Element gotoEl = (Element) gotos.item(i);
            String next = gotoEl.getAttribute("next");
            if (next != null && !next.isBlank()) {
                String targetId = normalizeTarget(next);
                if (nodeMap.containsKey(targetId)) {
                    if (isInsideField(gotoEl)) {
                        continue;
                    }
                    String sourcePort = "out";
                    if (sourceNode.getType() == FlowNodeType.TRANSFER) {
                        sourcePort = isInsideFilled(gotoEl) ? "success" : "fail";
                    }
                    model.addConnection(new FlowConnection(
                            "c_" + sourceId + "_" + targetId,
                            sourceId,
                            sourcePort,
                            targetId,
                            "in"
                    ));
                }
            }
        }

        // <choice> connections
        NodeList choices = formEl.getElementsByTagName("choice");
        for (int i = 0; i < choices.getLength(); i++) {
            Element choiceEl = (Element) choices.item(i);
            String next = choiceEl.getAttribute("next");
            if (next != null && !next.isBlank()) {
                String targetId = normalizeTarget(next);
                if (nodeMap.containsKey(targetId)) {
                    String dtmf = choiceEl.getAttribute("dtmf");
                    String value = choiceEl.getAttribute("value");
                    String accept = choiceEl.getAttribute("accept");
                    String sourcePort = deriveKeyFromDtmf(dtmf, value, accept, i + 1);

                    model.addConnection(new FlowConnection(
                            "c_" + sourceId + "_" + targetId + "_" + i,
                            sourceId,
                            sourcePort,
                            targetId,
                            "in"
                    ));
                }
            }
        }

        // <if> connections
        NodeList ifs = formEl.getElementsByTagName("if");
        for (int i = 0; i < ifs.getLength(); i++) {
            Element ifEl = (Element) ifs.item(i);
            String cond = ifEl.getAttribute("cond");
            String trueTarget = null;
            String falseTarget = null;

            NodeList ifChildren = ifEl.getChildNodes();
            for (int j = 0; j < ifChildren.getLength(); j++) {
                Node ifNode = ifChildren.item(j);
                if (ifNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element ifChildEl = (Element) ifNode;
                    if ("goto".equalsIgnoreCase(ifChildEl.getTagName())) {
                        String next = ifChildEl.getAttribute("next");
                        if (next != null && !next.isBlank()) {
                            if (trueTarget == null) {
                                trueTarget = normalizeTarget(next);
                            } else {
                                falseTarget = normalizeTarget(next);
                            }
                        }
                    }
                }
            }

            String truePort = (sourceNode.getType() == FlowNodeType.CONDITION || sourceNode.getType() == FlowNodeType.BUSINESS_HOURS || sourceNode.getType() == FlowNodeType.HOLIDAY)
                    ? "true"
                    : (sourceNode.getType() == FlowNodeType.INPUT || sourceNode.getType() == FlowNodeType.TRANSFER || sourceNode.getType() == FlowNodeType.API ? "success" : "out");

            String falsePort = (sourceNode.getType() == FlowNodeType.CONDITION || sourceNode.getType() == FlowNodeType.BUSINESS_HOURS || sourceNode.getType() == FlowNodeType.HOLIDAY)
                    ? "false"
                    : (sourceNode.getType() == FlowNodeType.INPUT || sourceNode.getType() == FlowNodeType.TRANSFER || sourceNode.getType() == FlowNodeType.API ? "error" : "out");

            if (trueTarget != null && nodeMap.containsKey(trueTarget)) {
                model.addConnection(new FlowConnection(
                        "c_" + sourceId + "_" + trueTarget + "_if_true",
                        sourceId,
                        truePort,
                        trueTarget,
                        "in"
                ));
            }
            if (falseTarget != null && nodeMap.containsKey(falseTarget)) {
                model.addConnection(new FlowConnection(
                        "c_" + sourceId + "_" + falseTarget + "_if_false",
                        sourceId,
                        falsePort,
                        falseTarget,
                        "in"
                ));
            }
        }

        // <field> filled/noinput/nomatch connections
        NodeList fields = formEl.getElementsByTagName("field");
        for (int i = 0; i < fields.getLength(); i++) {
            Element fieldEl = (Element) fields.item(i);
            String fieldName = fieldEl.getAttribute("name");

            NodeList fieldChildren = fieldEl.getChildNodes();
            for (int j = 0; j < fieldChildren.getLength(); j++) {
                Node fieldNode = fieldChildren.item(j);
                if (fieldNode.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                Element fieldChildEl = (Element) fieldNode;
                String tag = fieldChildEl.getTagName().toLowerCase(Locale.ROOT);

                if ("filled".equals(tag) || "noinput".equals(tag) || "nomatch".equals(tag)) {
                    NodeList filledChildren = fieldChildEl.getChildNodes();
                    for (int k = 0; k < filledChildren.getLength(); k++) {
                        Node filledNode = filledChildren.item(k);
                        if (filledNode.getNodeType() == Node.ELEMENT_NODE) {
                            Element filledEl = (Element) filledNode;
                            String filledTag = filledEl.getTagName().toLowerCase(Locale.ROOT);

                            if ("goto".equalsIgnoreCase(filledTag)) {
                                String next = filledEl.getAttribute("next");
                                if (next != null && !next.isBlank()) {
                                    String targetId = normalizeTarget(next);
                                    if (nodeMap.containsKey(targetId)) {
                                        String sourcePort = "success";
                                        model.addConnection(new FlowConnection(
                                                "c_" + sourceId + "_" + targetId + "_" + tag,
                                                sourceId,
                                                sourcePort,
                                                targetId,
                                                "in"
                                        ));
                                    }
                                }
                            } else if ("transfer".equalsIgnoreCase(filledTag)) {
                                String dest = filledEl.getAttribute("dest");
                                if (dest == null || dest.isBlank()) {
                                    dest = "agent";
                                }
                                String transferId = sourceId + "_transfer_" + dest.toLowerCase().replaceAll("[^a-z0-9]", "_");
                                FlowNode transferNode = new FlowNode(transferId, FlowNodeType.TRANSFER, "Transfer to " + dest);
                                transferNode.setTransfer(new FlowTransfer(dest));
                                model.addNode(transferNode);
                                String sourcePort = "success";
                                model.addConnection(new FlowConnection(
                                        "c_" + sourceId + "_" + transferId + "_" + tag,
                                        sourceId,
                                        sourcePort,
                                        transferId,
                                        "in"
                                ));
                            }
                        }
                    }
                }
            }
        }

        // <ai> options connections
        NodeList ais = formEl.getElementsByTagName("ai");
        for (int i = 0; i < ais.getLength(); i++) {
            Element aiEl = (Element) ais.item(i);
            String optionsStr = aiEl.getAttribute("options");
            if (optionsStr != null && !optionsStr.isBlank()) {
                for (String opt : optionsStr.split(",")) {
                    String[] parts = opt.split(":");
                    if (parts.length == 2) {
                        String port = parts[0].trim();
                        String targetId = parts[1].trim();
                        if (nodeMap.containsKey(targetId)) {
                            model.addConnection(new FlowConnection(
                                    "c_" + sourceId + "_" + targetId + "_" + port,
                                    sourceId,
                                    port,
                                    targetId,
                                    "in"
                            ));
                        }
                    }
                }
            }
        }
    }

    private FlowNodeType determineNodeType(Element formEl) {
        if ("menu".equalsIgnoreCase(formEl.getTagName()) || formEl.getElementsByTagName("menu").getLength() > 0) {
            return FlowNodeType.MENU;
        }
        if (formEl.getElementsByTagName("disconnect").getLength() > 0 ||
            formEl.getElementsByTagName("hangup").getLength() > 0) {
            return FlowNodeType.END;
        }
        if (formEl.getElementsByTagName("transfer").getLength() > 0) {
            return FlowNodeType.TRANSFER;
        }
        if (formEl.getElementsByTagName("field").getLength() > 0) {
            return FlowNodeType.INPUT;
        }
        if (formEl.getElementsByTagName("subdialog").getLength() > 0 ||
            formEl.getElementsByTagName("ai").getLength() > 0) {
            return FlowNodeType.AI;
        }
        if (formEl.hasAttribute("id")) {
            String id = formEl.getAttribute("id").toLowerCase(Locale.ROOT);
            if ("start".equals(id)) {
                return FlowNodeType.START;
            }
            if ("end".equals(id) || "hangup".equals(id)) {
                return FlowNodeType.END;
            }
        }

        NodeList children = formEl.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element el = (Element) node;
            String tag = el.getTagName().toLowerCase(Locale.ROOT);
            FlowNodeType type = FlowNodeType.fromVoiceXmlTag(tag);
            if (type != null) {
                return type;
            }
        }

        return FlowNodeType.PROMPT;
    }

    private String formatTitle(String id, FlowNodeType type) {
        if (id == null || id.isBlank()) {
            return "Node";
        }
        String cleaned = id.replaceAll("[-_]", " ").trim();
        if (cleaned.isEmpty()) {
            return "Node";
        }
        return Character.toUpperCase(cleaned.charAt(0)) + cleaned.substring(1);
    }

    private Document parseXml(String xml) throws ParserConfigurationException, SAXException, IOException {
        String trimmed = xml.trim();
        // Fix #10: Sanitize bare '&' characters that are not part of XML entities.
        // LLMs often output "Billing & Payments" instead of "Billing &amp; Payments",
        // causing SAXException: "The entity name must immediately follow the '&'".
        trimmed = sanitizeBareAmpersands(trimmed);
        DocumentBuilderFactory factory = com.nexusivr.ai.util.SecureXmlFactory.newDocumentBuilderFactory(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new org.xml.sax.InputSource(new StringReader(trimmed)));
    }

    /**
     * Fix #10: Escapes bare '&amp;' characters that are not already part of a recognized
     * XML entity reference (e.g., &amp;amp; &amp;lt; &amp;gt; &amp;apos; &amp;quot; or numeric &#NNN;).
     * <p>
     * This is a pre-parse sanitizer for LLM output that may contain unescaped ampersands
     * in prompt text (e.g., "Billing &amp; Payments").
     */
    private static String sanitizeBareAmpersands(String xml) {
        if (xml == null || !xml.contains("&")) {
            return xml;
        }
        // Replace & not followed by a valid XML entity pattern with &amp;
        // Valid entities: &amp; &lt; &gt; &apos; &quot; &#NNN; &#xHHH;
        return xml.replaceAll("&(?!amp;|lt;|gt;|apos;|quot;|#\\d+;|#x[0-9a-fA-F]+;)", "&amp;");
    }

    /**
     * Fix #14c: Narrowly-scoped pre-parse sanitizer for the specific malformed pattern
     * {@code <if cond="...">...</if><else>...</else>}, which the LLM sometimes emits
     * instead of the valid nested form {@code <if cond="...">...<else/>...</if>}.
     * <p>
     * This does NOT attempt to fix arbitrary malformed XML — only this one structural
     * VoiceXML error that has been observed in production.
     */
    private static String sanitizeMalformedIfElse(String vxml) {
        if (vxml == null || vxml.isBlank()) return vxml;

        String result = vxml;
        // Repeatedly rewrite the malformed sibling pattern until no more matches.
        // Pattern: <if ...> ... </if> <else> ... </else>
        // Becomes:  <if ...> ... <else/> ... </if>
        boolean changed;
        int safety = 0;
        do {
            changed = false;
            safety++;
            if (safety > 10) break;

            int ifEndIdx = result.indexOf("</if>");
            if (ifEndIdx < 0) break;

            int elseStartIdx = result.indexOf("<else>", ifEndIdx);
            if (elseStartIdx < 0 || elseStartIdx != ifEndIdx + 5) break;

            int elseEndIdx = result.indexOf("</else>", elseStartIdx);
            if (elseEndIdx < 0) break;

            int ifStartIdx = result.lastIndexOf("<if", ifEndIdx);
            if (ifStartIdx < 0) break;
            int ifTagEnd = result.indexOf(">", ifStartIdx);
            if (ifTagEnd < 0 || ifTagEnd >= ifEndIdx) break;

            String ifTag = result.substring(ifStartIdx, ifTagEnd + 1);
            String ifContent = result.substring(ifTagEnd + 1, ifEndIdx);
            String elseContent = result.substring(elseStartIdx + 6, elseEndIdx);

            String replacement = ifTag + ifContent + "<else/>" + elseContent + "</if>";
            result = result.substring(0, ifStartIdx) + replacement + result.substring(elseEndIdx + 7);
            changed = true;
        } while (changed);

        return result;
    }

    private static String formatFirstChars(String s) {
        if (s == null || s.isEmpty()) return "(empty)";
        int len = Math.min(20, s.length());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            sb.append(String.format("0x%02X ", (int) s.charAt(i)));
        }
        if (s.length() > 20) sb.append("...");
        return sb.toString();
    }

    private static String stripMarkdownCodeFences(String vxml) {
        String trimmed = vxml.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline >= 0) {
                int closingFence = trimmed.lastIndexOf("```");
                if (closingFence > firstNewline) {
                    return trimmed.substring(firstNewline + 1, closingFence).trim();
                }
            }
        }
        return trimmed;
    }

    private static boolean isInsideField(Node node) {
        Node current = node.getParentNode();
        while (current != null) {
            if (current.getNodeType() == Node.ELEMENT_NODE && "field".equalsIgnoreCase(((Element) current).getTagName())) {
                return true;
            }
            current = current.getParentNode();
        }
        return false;
    }

    private static String findPrecedingCommentTitle(Element el) {
        Node sibling = el.getPreviousSibling();
        while (sibling != null) {
            if (sibling.getNodeType() == Node.COMMENT_NODE) {
                String commentText = sibling.getNodeValue().trim();
                if (commentText.contains("(")) {
                    commentText = commentText.substring(0, commentText.indexOf('(')).trim();
                }
                if (commentText.contains(":")) {
                    commentText = commentText.substring(0, commentText.indexOf(':')).trim();
                }
                if (commentText.toLowerCase().startsWith("type")) {
                    // skip
                } else if (!commentText.isEmpty()) {
                    return commentText;
                }
            } else if (sibling.getNodeType() == Node.TEXT_NODE) {
                if (!sibling.getNodeValue().trim().isEmpty()) {
                    break;
                }
            } else {
                break;
            }
            sibling = sibling.getPreviousSibling();
        }
        return null;
    }

    private static boolean isInsideFilled(Node node) {
        Node current = node.getParentNode();
        while (current != null) {
            if (current.getNodeType() == Node.ELEMENT_NODE && "filled".equalsIgnoreCase(((Element) current).getTagName())) {
                return true;
            }
            current = current.getParentNode();
        }
        return false;
    }
}
