package gov.iti.telecom.platform;

import java.io.IOException;
import java.io.OutputStream;

import javax.sound.sampled.AudioFormat;

import org.jvoicexml.ConnectionInformation;
import org.jvoicexml.CallControlProperties;
import org.jvoicexml.event.error.NoresourceError;
import org.jvoicexml.implementation.ExternalResource;
import org.jvoicexml.implementation.SynthesizedOutput;
import org.jvoicexml.implementation.SpokenInput;
import org.jvoicexml.implementation.Telephony;
import org.jvoicexml.implementation.TelephonyListener;

public class AsteriskTelephony implements Telephony, ExternalResource {
    private final int id = java.util.UUID.randomUUID().hashCode();
    private boolean active = true;

    public AsteriskTelephony() {
        System.out.println("[AsteriskTelephony] constructor id=" + id);
    }

    @Override
    public void connect(ConnectionInformation info) throws IOException {
    }

    @Override
    public void disconnect(ConnectionInformation info) {
    }

    @Override
    public String getType() {
        return "asterisk-call-control";
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
    public void play(SynthesizedOutput output, CallControlProperties properties)
            throws NoresourceError, IOException {
        String msg = "[AsteriskTelephony] play called! output=" + output + ", properties=" + properties;
        System.out.println(msg);
        System.err.println(msg);
    }

    @Override
    public void stopPlay() throws NoresourceError {
    }

    @Override
    public void record(SpokenInput input, CallControlProperties properties)
            throws NoresourceError, IOException {
    }

    @Override
    public AudioFormat getRecordingAudioFormat() {
        return new AudioFormat(8000, 16, 1, true, false);
    }

    @Override
    public void startRecording(SpokenInput input, OutputStream out, CallControlProperties properties)
            throws NoresourceError, IOException {
    }

    @Override
    public void stopRecording() throws NoresourceError {
    }

    @Override
    public void transfer(String destination) throws NoresourceError {
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void hangup() {
        active = false;
    }

    @Override
    public void addListener(TelephonyListener listener) {
    }

    @Override
    public void removeListener(TelephonyListener listener) {
    }
}
