package gov.iti.telecom.platform;

import java.io.IOException;
import java.net.URI;
import java.util.Collection;

import org.jvoicexml.ConnectionInformation;
import org.jvoicexml.event.error.BadFetchError;
import org.jvoicexml.event.error.NoresourceError;
import org.jvoicexml.event.error.UnsupportedFormatError;
import org.jvoicexml.event.error.UnsupportedLanguageError;
import org.jvoicexml.implementation.ExternalResource;
import org.jvoicexml.implementation.GrammarImplementation;
import org.jvoicexml.implementation.SpokenInput;
import org.jvoicexml.implementation.SpokenInputListener;

public class AsteriskSpokenInput implements SpokenInput, ExternalResource {
    private int id;

    public AsteriskSpokenInput() {
        id = java.util.UUID.randomUUID().hashCode();
        System.out.println("[AsteriskSpokenInput] constructor id=" + id);
    }

    @Override
    public void connect(ConnectionInformation info) throws IOException {
    }

    @Override
    public void disconnect(ConnectionInformation info) {
    }

    @Override
    public String getType() {
        return "dummy";
    }

    @Override
    public void open() throws NoresourceError {
    }

    @Override
    public void activate() throws NoresourceError {
    }

    @Override
    public void passivate() throws NoresourceError {
    }

    @Override
    public void close() {
    }

    @Override
    public boolean isBusy() {
        return false;
    }

    @Override
    public void startRecognition(org.jvoicexml.interpreter.datamodel.DataModel model,
            org.jvoicexml.SpeechRecognizerProperties speechProperties,
            org.jvoicexml.DtmfRecognizerProperties dtmfProperties) throws NoresourceError, BadFetchError {
        System.out.println("[AsteriskSpokenInput#" + id + "] startRecognition called");
    }

    @Override
    public void stopRecognition() {
        System.out.println("[AsteriskSpokenInput#" + id + "] stopRecognition called");
    }

    @Override
    public Collection<org.jvoicexml.xml.srgs.GrammarType> getSupportedGrammarTypes() {
        return java.util.Collections.emptyList();
    }

    @Override
    public void activateGrammars(Collection<GrammarImplementation<?>> grammars)
            throws org.jvoicexml.event.error.BadFetchError, UnsupportedLanguageError, UnsupportedFormatError, NoresourceError {
        System.out.println("[AsteriskSpokenInput#" + id + "] activateGrammars called");
    }

    @Override
    public void deactivateGrammars(Collection<GrammarImplementation<?>> grammars) throws NoresourceError, org.jvoicexml.event.error.BadFetchError {
        System.out.println("[AsteriskSpokenInput#" + id + "] deactivateGrammars called");
    }

    @Override
    public GrammarImplementation<?> loadGrammar(URI uri, org.jvoicexml.xml.srgs.GrammarType type)
            throws NoresourceError, java.io.IOException, UnsupportedFormatError {
        System.out.println("[AsteriskSpokenInput#" + id + "] loadGrammar called");
        return null;
    }

    @Override
    public Collection<org.jvoicexml.xml.vxml.BargeInType> getSupportedBargeInTypes() {
        return java.util.Collections.emptyList();
    }

    @Override
    public void addListener(SpokenInputListener listener) {
    }

    @Override
    public void removeListener(SpokenInputListener listener) {
    }
}
