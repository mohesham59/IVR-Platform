package gov.iti.telecom.platform;

import java.util.Collection;

import org.jvoicexml.SpeakableText;
import org.jvoicexml.event.error.BadFetchError;
import org.jvoicexml.event.error.NoresourceError;
import org.jvoicexml.implementation.ExternalResource;
import org.jvoicexml.implementation.OutputDevice;
import org.jvoicexml.implementation.SynthesizedOutput;
import org.jvoicexml.implementation.SynthesizedOutputListener;
import org.jvoicexml.implementation.SynthesizedOutputProvider;
import org.jvoicexml.xml.srgs.ModeType;

public class AsteriskSynthesizedOutput implements SynthesizedOutput, ExternalResource, OutputDevice, SynthesizedOutputProvider {
    private final int id = java.util.UUID.randomUUID().hashCode();

    public AsteriskSynthesizedOutput() {
        System.out.println("[AsteriskSynthesizedOutput] constructor id=" + id);
    }

    @Override
    public void connect(org.jvoicexml.ConnectionInformation info) throws java.io.IOException {
    }

    @Override
    public void disconnect(org.jvoicexml.ConnectionInformation info) {
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
    public boolean supportsBargeIn() {
        return true;
    }

    @Override
    public void cancelOutput(org.jvoicexml.xml.vxml.BargeInType bargeInType) throws NoresourceError {
    }

    @Override
    public void queueSpeakable(SpeakableText speakable, String sessionId, org.jvoicexml.DocumentServer documentServer)
            throws NoresourceError, BadFetchError {
        String msg = "[AsteriskSynthesizedOutput#" + id + "] queueSpeakable called! speakable=" + speakable;
        System.out.println(msg);
        System.err.println(msg);
    }

    @Override
    public void waitNonBargeInPlayed() {
        String msg = "[AsteriskSynthesizedOutput#" + id + "] waitNonBargeInPlayed called";
        System.out.println(msg);
        System.err.println(msg);
    }

    @Override
    public void waitQueueEmpty() {
        String msg = "[AsteriskSynthesizedOutput#" + id + "] waitQueueEmpty called";
        System.out.println(msg);
        System.err.println(msg);
    }

    @Override
    public void addListener(SynthesizedOutputListener listener) {
    }

    @Override
    public void removeListener(SynthesizedOutputListener listener) {
    }

    @Override
    public SynthesizedOutput getSynthesizedOutput() {
        return this;
    }
}
