package gov.iti.telecom.platform;

import org.jvoicexml.event.error.NoresourceError;
import org.jvoicexml.implementation.ExternalResource;
import org.jvoicexml.implementation.ResourceFactory;
import org.jvoicexml.implementation.SpokenInput;

public class AsteriskSpokenInputFactory implements ResourceFactory<SpokenInput> {
    @Override
    public Class<SpokenInput> getResourceType() {
        return SpokenInput.class;
    }

    @Override
    public SpokenInput createResource() throws NoresourceError {
        return new AsteriskSpokenInput();
    }

    @Override
    public int getInstances() {
        return 1;
    }

    @Override
    public String getType() {
        return "asterisk-input";
    }
}
