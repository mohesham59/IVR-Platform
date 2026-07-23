package gov.iti.telecom.vxml;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/**
 * VxmlParser — DOM-based parser for W3C VoiceXML 2.1 documents.
 */
public class VxmlParser {

    public static VxmlDocument parse(File xmlFile) throws Exception {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setNamespaceAware(true);
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(xmlFile);
        doc.getDocumentElement().normalize();
        return parseDocument(doc);
    }

    public static VxmlDocument parse(String xmlContent) throws Exception {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setNamespaceAware(true);
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        InputStream is = new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8));
        Document doc = dBuilder.parse(is);
        doc.getDocumentElement().normalize();
        return parseDocument(doc);
    }

    private static VxmlDocument parseDocument(Document doc) {
        Element root = doc.getDocumentElement();
        if (!"vxml".equalsIgnoreCase(root.getLocalName()) && !"vxml".equalsIgnoreCase(root.getTagName())) {
            throw new IllegalArgumentException("Root element must be <vxml>");
        }

        VxmlDocument vxmlDoc = new VxmlDocument();
        if (root.hasAttribute("version")) {
            vxmlDoc.setVersion(root.getAttribute("version"));
        }

        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element elem = (Element) node;
            String tagName = elem.getLocalName() != null ? elem.getLocalName() : elem.getTagName();

            if ("var".equalsIgnoreCase(tagName)) {
                vxmlDoc.addVariable(elem.getAttribute("name"), elem.getAttribute("expr"));
            } else if ("property".equalsIgnoreCase(tagName)) {
                vxmlDoc.addProperty(elem.getAttribute("name"), elem.getAttribute("value"));
            } else if ("form".equalsIgnoreCase(tagName)) {
                vxmlDoc.addDialog(parseForm(elem));
            } else if ("menu".equalsIgnoreCase(tagName)) {
                vxmlDoc.addDialog(parseMenu(elem));
            }
        }

        return vxmlDoc;
    }

    private static VxmlForm parseForm(Element formElem) {
        String id = formElem.getAttribute("id");
        if (id.isEmpty()) {
            id = "form_" + System.identityHashCode(formElem);
        }

        VxmlForm form = new VxmlForm(id);

        NodeList children = formElem.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element elem = (Element) node;
            String tagName = elem.getLocalName() != null ? elem.getLocalName() : elem.getTagName();

            if ("block".equalsIgnoreCase(tagName)) {
                parseBlock(elem, form);
            } else if ("field".equalsIgnoreCase(tagName)) {
                parseField(elem, form);
            } else if ("transfer".equalsIgnoreCase(tagName)) {
                form.setTransferDest(elem.getAttribute("dest"));
            } else if ("disconnect".equalsIgnoreCase(tagName) || "exit".equalsIgnoreCase(tagName)) {
                form.setDisconnect(true);
            }
        }

        return form;
    }

    private static void parseBlock(Element blockElem, VxmlForm form) {
        NodeList children = blockElem.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element elem = (Element) node;
            String tagName = elem.getLocalName() != null ? elem.getLocalName() : elem.getTagName();

            if ("prompt".equalsIgnoreCase(tagName)) {
                form.setPrompt(elem.getTextContent().trim());
            } else if ("audio".equalsIgnoreCase(tagName)) {
                form.setAudioSrc(elem.getAttribute("src"));
            } else if ("goto".equalsIgnoreCase(tagName)) {
                String next = elem.getAttribute("next");
                if (next.startsWith("#")) {
                    next = next.substring(1);
                }
                form.setNextTarget(next);
            } else if ("disconnect".equalsIgnoreCase(tagName) || "exit".equalsIgnoreCase(tagName)) {
                form.setDisconnect(true);
            }
        }
    }

    private static void parseField(Element fieldElem, VxmlForm form) {
        form.setFieldName(fieldElem.getAttribute("name"));

        NodeList children = fieldElem.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element elem = (Element) node;
            String tagName = elem.getLocalName() != null ? elem.getLocalName() : elem.getTagName();

            if ("prompt".equalsIgnoreCase(tagName)) {
                form.setPrompt(elem.getTextContent().trim());
            } else if ("audio".equalsIgnoreCase(tagName)) {
                form.setAudioSrc(elem.getAttribute("src"));
            } else if ("filled".equalsIgnoreCase(tagName)) {
                NodeList filledChildren = elem.getChildNodes();
                for (int j = 0; j < filledChildren.getLength(); j++) {
                    Node fNode = filledChildren.item(j);
                    if (fNode.getNodeType() == Node.ELEMENT_NODE) {
                        Element fElem = (Element) fNode;
                        String fTag = fElem.getLocalName() != null ? fElem.getLocalName() : fElem.getTagName();
                        if ("goto".equalsIgnoreCase(fTag)) {
                            String next = fElem.getAttribute("next");
                            if (next.startsWith("#")) {
                                next = next.substring(1);
                            }
                            form.setNextTarget(next);
                        }
                    }
                }
            }
        }
    }

    private static VxmlMenu parseMenu(Element menuElem) {
        String id = menuElem.getAttribute("id");
        if (id.isEmpty()) {
            id = "menu_" + System.identityHashCode(menuElem);
        }

        VxmlMenu menu = new VxmlMenu(id);

        NodeList children = menuElem.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element elem = (Element) node;
            String tagName = elem.getLocalName() != null ? elem.getLocalName() : elem.getTagName();

            if ("prompt".equalsIgnoreCase(tagName)) {
                menu.setPrompt(elem.getTextContent().trim());
            } else if ("audio".equalsIgnoreCase(tagName)) {
                menu.setAudioSrc(elem.getAttribute("src"));
            } else if ("choice".equalsIgnoreCase(tagName)) {
                String dtmf = elem.getAttribute("dtmf");
                String next = elem.getAttribute("next");
                if (next.startsWith("#")) {
                    next = next.substring(1);
                }
                String label = elem.getTextContent().trim();
                menu.addChoice(new VxmlChoice(dtmf, next, label));
            }
        }

        return menu;
    }
}
