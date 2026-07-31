package gov.iti.telecom.platform;

import java.net.URI;

import org.jvoicexml.profile.Profile;
import org.jvoicexml.profile.SsmlParsingStrategy;
import org.jvoicexml.profile.SsmlParsingStrategyFactory;
import org.jvoicexml.profile.TagStrategy;
import org.jvoicexml.profile.TagStrategyFactory;
import org.jvoicexml.xml.VoiceXmlNode;
import org.w3c.dom.Node;

public class MinimalProfile implements Profile {

    @Override
    public String getName() {
        return "default";
    }

    @Override
    public void initialize(org.jvoicexml.interpreter.VoiceXmlInterpreterContext context) {
    }

    @Override
    public void terminate(org.jvoicexml.interpreter.VoiceXmlInterpreterContext context) {
    }

    @Override
    public TagStrategyFactory getInitializationTagStrategyFactory() {
        return new TagStrategyFactory() {
            @Override
            public URI getTagNamespace() throws java.net.URISyntaxException {
                return new URI("http://www.w3.org/2001/vxml");
            }

            @Override
            public TagStrategy getTagStrategy(Node node) {
                return null;
            }

            @Override
            public TagStrategy getTagStrategy(String tagName) {
                return null;
            }
        };
    }

    @Override
    public TagStrategyFactory getTagStrategyFactory() {
        return new TagStrategyFactory() {
            @Override
            public URI getTagNamespace() throws java.net.URISyntaxException {
                return new URI("http://www.w3.org/2001/vxml");
            }

            @Override
            public TagStrategy getTagStrategy(Node node) {
                return null;
            }

            @Override
            public TagStrategy getTagStrategy(String tagName) {
                return null;
            }
        };
    }

    @Override
    public SsmlParsingStrategyFactory getSsmlParsingStrategyFactory() {
        return new SsmlParsingStrategyFactory() {
            @Override
            public SsmlParsingStrategy getParsingStrategy(VoiceXmlNode node) {
                return null;
            }
        };
    }
}
