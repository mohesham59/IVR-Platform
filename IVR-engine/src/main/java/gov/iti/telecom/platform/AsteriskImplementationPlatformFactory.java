package gov.iti.telecom.platform;

import java.io.IOException;

import org.jvoicexml.Configuration;
import org.jvoicexml.ConnectionInformation;
import org.jvoicexml.ConfigurationException;
import org.jvoicexml.ImplementationPlatform;
import org.jvoicexml.event.error.NoresourceError;
import org.jvoicexml.implementation.PlatformFactory;
import org.jvoicexml.implementation.ResourceFactory;
import org.jvoicexml.implementation.SpokenInput;
import org.jvoicexml.implementation.SynthesizedOutput;
import org.jvoicexml.implementation.Telephony;
import org.jvoicexml.implementation.jvxml.JVoiceXmlImplementationPlatform;
import org.jvoicexml.implementation.pool.KeyedResourcePool;

public class AsteriskImplementationPlatformFactory implements org.jvoicexml.ImplementationPlatformFactory {
    private final ResourceFactory<SynthesizedOutput> synthesizedOutputFactory;
    private final ResourceFactory<SpokenInput> spokenInputFactory;
    private final ResourceFactory<Telephony> telephonyFactory;
    private boolean initialized = false;

    public AsteriskImplementationPlatformFactory() {
        this.synthesizedOutputFactory = new AsteriskSynthesizedOutputFactory();
        this.spokenInputFactory = new AsteriskSpokenInputFactory();
        this.telephonyFactory = new AsteriskTelephonyFactory();
    }

    @Override
    public void init(Configuration config) throws ConfigurationException {
        initialized = true;
    }

    @Override
    public ImplementationPlatform getImplementationPlatform(ConnectionInformation info) throws NoresourceError {
        if (!initialized) {
            throw new NoresourceError("Factory not initialized");
        }

        System.out.println("[AsteriskImplementationPlatformFactory] getImplementationPlatform called!");

        try {
            KeyedResourcePool<org.jvoicexml.implementation.SynthesizedOutput> synthesizerPool = 
                    new KeyedResourcePool<>();
            synthesizerPool.addResourceFactory(synthesizedOutputFactory);

            KeyedResourcePool<SpokenInput> spokenPool = 
                    new KeyedResourcePool<>();
            spokenPool.addResourceFactory(spokenInputFactory);

            KeyedResourcePool<org.jvoicexml.implementation.Telephony> telephonyPool = 
                    new KeyedResourcePool<>();
            telephonyPool.addResourceFactory(telephonyFactory);

            // Use reflection to access the package-private constructor
            java.lang.reflect.Constructor<?> constructor = null;
            for (java.lang.reflect.Constructor<?> ctor : JVoiceXmlImplementationPlatform.class.getDeclaredConstructors()) {
                if (ctor.getParameterTypes().length == 4) {
                    constructor = ctor;
                    break;
                }
            }
            if (constructor == null) {
                throw new NoresourceError("Could not find 4-arg JVoiceXmlImplementationPlatform constructor");
            }
            constructor.setAccessible(true);
            
            ImplementationPlatform platform = (ImplementationPlatform) constructor.newInstance(
                    telephonyPool, synthesizerPool, spokenPool, info);

            org.jvoicexml.SystemOutput systemOutput = platform.getSystemOutput();
            System.out.println("[AsteriskImplementationPlatformFactory] platform.getSystemOutput()=" + systemOutput);
            System.out.println("[AsteriskImplementationPlatformFactory] platform.getSystemOutput().getClass()=" + (systemOutput != null ? systemOutput.getClass().getName() : "null"));

            return platform;
        } catch (Throwable e) {
            throw new NoresourceError("Failed to create implementation platform", e);
        }
    }

    @Override
    public void close() {
        // Cleanup if needed
    }
}
