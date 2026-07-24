package gov.iti.telecom.platform;

import java.io.Serializable;
import java.net.URI;

import org.jvoicexml.ConnectionInformation;

public class AsteriskConnectionInformation implements ConnectionInformation, Serializable {
    private static final long serialVersionUID = 1L;

    private final String profile;
    private final String systemOutput;
    private final String userInput;
    private final String callControl;
    private final URI calledDevice;
    private final URI callingDevice;
    private final String protocolName;
    private final String protocolVersion;

    public AsteriskConnectionInformation(String profile,
                                         String systemOutput,
                                         String userInput,
                                         String callControl,
                                         URI calledDevice,
                                         URI callingDevice,
                                         String protocolName,
                                         String protocolVersion) {
        this.profile = profile;
        this.systemOutput = systemOutput;
        this.userInput = userInput;
        this.callControl = callControl;
        this.calledDevice = calledDevice;
        this.callingDevice = callingDevice;
        this.protocolName = protocolName;
        this.protocolVersion = protocolVersion;
    }

    @Override
    public String getProfile() {
        return profile;
    }

    @Override
    public String getSystemOutput() {
        return systemOutput;
    }

    @Override
    public String getUserInput() {
        return userInput;
    }

    @Override
    public String getCallControl() {
        return callControl;
    }

    @Override
    public URI getCalledDevice() {
        return calledDevice;
    }

    @Override
    public URI getCallingDevice() {
        return callingDevice;
    }

    @Override
    public String getProtocolName() {
        return protocolName;
    }

    @Override
    public String getProtocolVersion() {
        return protocolVersion;
    }
}
