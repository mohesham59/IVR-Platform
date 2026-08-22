package com.nexusivr.ai.util;

/**
 * Utility for formatting XML strings with indentation for readable log output.
 * Does not alter the actual XML content, only the whitespace representation.
 */
public class XmlLogFormatter {

    private XmlLogFormatter() {}

    /**
     * Formats an XML string with 4-space indentation and line numbers for readable log output.
     *
     * @param xml raw XML string
     * @return pretty-printed XML with line numbers, or the original string if parsing fails
     */
    public static String prettyPrintWithLineNumbers(String xml) {
        if (xml == null || xml.isBlank()) {
            return xml;
        }
        try {
            String[] lines = prettyPrint(xml).split("\n");
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < lines.length; i++) {
                result.append(String.format("%3d | %s%n", i + 1, lines[i]));
            }
            return result.toString().trim();
        } catch (Exception e) {
            return xml;
        }
    }

    /**
     * Formats an XML string with 4-space indentation for readable log output.
     *
     * @param xml raw XML string
     * @return pretty-printed XML, or the original string if parsing fails
     */
    public static String prettyPrint(String xml) {
        if (xml == null || xml.isBlank()) {
            return xml;
        }
        try {
            javax.xml.parsers.DocumentBuilderFactory factory =
                    com.nexusivr.ai.util.SecureXmlFactory.newDocumentBuilderFactory(false);
            javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
            org.w3c.dom.Document doc = builder.parse(new org.xml.sax.InputSource(new java.io.StringReader(xml.trim())));

            javax.xml.transform.TransformerFactory tf = javax.xml.transform.TransformerFactory.newInstance();
            javax.xml.transform.Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

            java.io.StringWriter writer = new java.io.StringWriter();
            transformer.transform(new javax.xml.transform.dom.DOMSource(doc), new javax.xml.transform.stream.StreamResult(writer));
            return writer.toString().trim();
        } catch (Exception e) {
            return simpleFormat(xml);
        }
    }

    private static String simpleFormat(String xml) {
        if (xml == null) return "";
        return xml.replaceAll("><", ">\n<").trim();
    }
}
