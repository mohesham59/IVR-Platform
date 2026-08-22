package gov.iti.telecom.platform;

import java.io.IOException;

import org.jvoicexml.event.error.NoresourceError;
import org.jvoicexml.implementation.ExternalResource;
import org.jvoicexml.implementation.ResourceFactory;
import org.jvoicexml.implementation.Telephony;

public class AsteriskTelephonyFactory implements ResourceFactory<Telephony> {
    @Override
    public Class<Telephony> getResourceType() {
        return Telephony.class;
    }

    @Override
    public Telephony createResource() throws NoresourceError {
        return new AsteriskTelephony();
    }

    @Override
    public int getInstances() {
        return 1;
    }

    @Override
    public String getType() {
        return "asterisk-call-control";
    }
}
