package com.nexusivr.ai.service;

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

/**
 * Parses VoiceXML 2.1 documents into an intermediate model that the
 * {@link VxmlToFlowConverter} can translate into the standard IVR Builder
 * node/edge JSON format.
 */
public class VxmlParser {

    public VxmlDocument parse(String vxmlContent) throws VxmlParseException {
        if (vxmlContent == null || vxmlContent.isBlank()) {
            throw new VxmlParseException("VXML content is empty");
        }

        Document doc;
        try {
            doc = parseXml(vxmlContent);
        } catch (Exception e) {
            throw new VxmlParseException("Failed to parse VXML XML: " + e.getMessage(), e);
        }

        Element root = doc.getDocumentElement();
        if (!"vxml".equalsIgnoreCase(root.getTagName())) {
            throw new VxmlParseException("Root element must be <vxml>, got: " + root.getTagName());
        }

        VxmlDocument document = new VxmlDocument();
        document.setVersion(root.getAttribute("version"));

        NodeList formNodes = root.getElementsByTagName("form");
        for (int i = 0; i < formNodes.getLength(); i++) {
            Element formEl = (Element) formNodes.item(i);
            document.addForm(parseForm(formEl));
        }

        return document;
    }

    private VxmlForm parseForm(Element formEl) {
        String id = formEl.getAttribute("id");
        if (id == null || id.isBlank()) {
            id = "form_" + UUID.randomUUID().toString().substring(0, 8);
        }

        VxmlForm form = new VxmlForm(id);

        NodeList children = formEl.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element el = (Element) node;
            String tag = el.getTagName().toLowerCase(Locale.ROOT);

            switch (tag) {
                case "block" -> form.setBlock(parseBlock(el));
                case "field" -> form.addField(parseField(el));
                case "menu" -> form.setMenu(parseMenu(el));
                case "transfer" -> form.setTransfer(parseTransfer(el));
                case "prompt" -> form.addPrompt(parsePrompt(el));
                case "disconnect", "hangup" -> form.setDisconnect(true);
                case "if" -> form.addIf(parseIf(el));
                default -> {
                }
            }
        }

        return form;
    }

    private VxmlBlock parseBlock(Element blockEl) {
        VxmlBlock block = new VxmlBlock();
        NodeList children = blockEl.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element el = (Element) node;
            String tag = el.getTagName().toLowerCase(Locale.ROOT);

            switch (tag) {
                case "prompt" -> block.addPrompt(parsePrompt(el));
                case "goto" -> block.setGoto(parseGoto(el));
                case "if" -> block.setIf(parseIf(el));
                case "transfer" -> block.setTransfer(parseTransfer(el));
                case "disconnect", "hangup" -> block.setDisconnect(true);
                default -> {
                }
            }
        }
        return block;
    }

    private VxmlPrompt parsePrompt(Element promptEl) {
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
                } else {
                    text.append(el.getTextContent().trim());
                }
            }
        }
        return new VxmlPrompt(text.toString().trim());
    }

    private VxmlGoto parseGoto(Element gotoEl) {
        String next = gotoEl.getAttribute("next");
        String expr = gotoEl.getAttribute("expr");
        return new VxmlGoto(
                next.isBlank() ? null : next,
                expr.isBlank() ? null : expr
        );
    }

    private VxmlTransfer parseTransfer(Element transferEl) {
        String dest = transferEl.getAttribute("dest");
        return new VxmlTransfer(dest);
    }

    private VxmlField parseField(Element fieldEl) {
        String name = fieldEl.getAttribute("name");
        String type = fieldEl.getAttribute("type");
        VxmlField field = new VxmlField(name, type);

        NodeList children = fieldEl.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element el = (Element) node;
            String tag = el.getTagName().toLowerCase(Locale.ROOT);

            switch (tag) {
                case "prompt" -> field.addPrompt(parsePrompt(el));
                case "grammar" -> field.setGrammar(parseGrammar(el));
                case "filled" -> field.setFilled(parseFilled(el));
                case "noinput" -> field.setNoInput(parseBlock(el));
                case "nomatch" -> field.setNoMatch(parseBlock(el));
                default -> {
                }
            }
        }
        return field;
    }

    private VxmlGrammar parseGrammar(Element grammarEl) {
        String mode = grammarEl.getAttribute("mode");
        String version = grammarEl.getAttribute("version");
        List<String> tokens = new ArrayList<>();
        NodeList items = grammarEl.getElementsByTagName("item");
        for (int i = 0; i < items.getLength(); i++) {
            tokens.add(items.item(i).getTextContent().trim());
        }
        if (tokens.isEmpty()) {
            NodeList children = grammarEl.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                if (children.item(i).getNodeType() == Node.TEXT_NODE) {
                    tokens.add(children.item(i).getTextContent().trim());
                }
            }
        }
        return new VxmlGrammar(mode, version, tokens);
    }

    private VxmlBlock parseFilled(Element filledEl) {
        VxmlBlock block = new VxmlBlock();
        NodeList children = filledEl.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element el = (Element) node;
            String tag = el.getTagName().toLowerCase(Locale.ROOT);
            switch (tag) {
                case "prompt" -> block.addPrompt(parsePrompt(el));
                case "goto" -> block.setGoto(parseGoto(el));
                case "if" -> block.setIf(parseIf(el));
                case "transfer" -> block.setTransfer(parseTransfer(el));
                case "disconnect", "hangup" -> block.setDisconnect(true);
                default -> {
                }
            }
        }
        return block;
    }

    private VxmlMenu parseMenu(Element menuEl) {
        String id = menuEl.getAttribute("id");
        VxmlMenu menu = new VxmlMenu(id);

        NodeList children = menuEl.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element el = (Element) node;
            String tag = el.getTagName().toLowerCase(Locale.ROOT);
            switch (tag) {
                case "prompt" -> menu.addPrompt(parsePrompt(el));
                case "choice" -> menu.addChoice(parseChoice(el));
                case "else" -> menu.setElseGoto(parseGoto(el));
                default -> {
                }
            }
        }
        return menu;
    }

    private VxmlChoice parseChoice(Element choiceEl) {
        String next = choiceEl.getAttribute("next");
        String accept = choiceEl.getAttribute("accept");
        String dtmf = choiceEl.getAttribute("dtmf");
        String value = choiceEl.getAttribute("value");
        return new VxmlChoice(next, accept, dtmf, value, choiceEl.getTextContent().trim());
    }

    private VxmlIf parseIf(Element ifEl) {
        String cond = ifEl.getAttribute("cond");
        VxmlIf ifObj = new VxmlIf(cond);

        NodeList children = ifEl.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element el = (Element) node;
            String tag = el.getTagName().toLowerCase(Locale.ROOT);
            switch (tag) {
                case "goto" -> ifObj.setGoto(parseGoto(el));
                case "prompt" -> ifObj.addPrompt(parsePrompt(el));
                case "transfer" -> ifObj.setTransfer(parseTransfer(el));
                case "disconnect", "hangup" -> ifObj.setDisconnect(true);
                default -> {
                }
            }
        }

        NodeList elseIfs = ifEl.getElementsByTagName("elseif");
        for (int i = 0; i < elseIfs.getLength(); i++) {
            ifObj.addElseIf(parseIf((Element) elseIfs.item(i)));
        }

        NodeList elseNodes = ifEl.getElementsByTagName("else");
        if (elseNodes.getLength() > 0) {
            Element elseEl = (Element) elseNodes.item(0);
            VxmlIf elseIf = new VxmlIf("else");
            NodeList elseChildren = elseEl.getChildNodes();
            for (int i = 0; i < elseChildren.getLength(); i++) {
                Node node = elseChildren.item(i);
                if (node.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                Element el = (Element) node;
                String tag = el.getTagName().toLowerCase(Locale.ROOT);
                switch (tag) {
                    case "goto" -> elseIf.setGoto(parseGoto(el));
                    case "prompt" -> elseIf.addPrompt(parsePrompt(el));
                    case "transfer" -> elseIf.setTransfer(parseTransfer(el));
                    case "disconnect", "hangup" -> elseIf.setDisconnect(true);
                    default -> {
                    }
                }
            }
            ifObj.setElseBranch(elseIf);
        }

        return ifObj;
    }

    private Document parseXml(String xml) throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory factory = com.nexusivr.ai.util.SecureXmlFactory.newDocumentBuilderFactory(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new org.xml.sax.InputSource(new StringReader(xml)));
    }
}
