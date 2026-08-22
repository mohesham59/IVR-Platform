package com.nexusivr.ai.util;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Builds {@link DocumentBuilderFactory} instances hardened against XXE
 * (external entity / doctype injection). All user-supplied VXML content should
 * be parsed with a factory returned by this class.
 */
public final class SecureXmlFactory {

    private SecureXmlFactory() {
    }

    public static DocumentBuilderFactory newDocumentBuilderFactory(boolean namespaceAware) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(namespaceAware);
        factory.setValidating(false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        } catch (javax.xml.parsers.ParserConfigurationException e) {
            throw new IllegalStateException("Could not configure secure XML parser", e);
        }
        return factory;
    }
}
