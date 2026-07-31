package gov.iti.telecom.platform;

import org.jvoicexml.event.error.NoresourceError;
import org.jvoicexml.implementation.ExternalResource;
import org.jvoicexml.implementation.ResourceFactory;
import org.jvoicexml.implementation.SynthesizedOutput;

public class AsteriskSynthesizedOutputFactory implements ResourceFactory<SynthesizedOutput> {
    @Override
    public Class<SynthesizedOutput> getResourceType() {
        return SynthesizedOutput.class;
    }

    @Override
    public SynthesizedOutput createResource() throws NoresourceError {
        AsteriskSynthesizedOutput instance = new AsteriskSynthesizedOutput();
        System.out.println("[AsteriskSynthesizedOutputFactory] created instance id=" + instance.hashCode());
        return instance;
    }

    @Override
    public int getInstances() {
        return 1;
    }

    @Override
    public String getType() {
        return "dummy";
    }
}
