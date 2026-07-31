package gov.iti.telecom.platform;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.jvoicexml.Configuration;
import org.jvoicexml.ConfigurationException;
import org.jvoicexml.DocumentServer;
import org.jvoicexml.ImplementationPlatformFactory;
import org.jvoicexml.interpreter.DialogFactory;
import org.jvoicexml.interpreter.GrammarProcessor;
import org.jvoicexml.interpreter.datamodel.DataModel;
import org.jvoicexml.interpreter.dialog.JVoiceXmlDialogFactory;
import org.jvoicexml.interpreter.grammar.JVoiceXmlGrammarProcessor;
import org.jvoicexml.profile.Profile;
import org.jvoicexml.documentserver.JVoiceXmlDocumentServer;
import org.jvoicexml.documentserver.SchemeStrategy;
import org.jvoicexml.documentserver.schemestrategy.FileSchemeStrategy;

public class MinimalConfiguration implements Configuration {

    private final Map<Class<?>, Object> instances = new HashMap<>();
    private final Map<String, Profile> profiles = new HashMap<>();

    public MinimalConfiguration() {
        // Pre-register our custom implementation platform factory
        instances.put(ImplementationPlatformFactory.class, new AsteriskImplementationPlatformFactory());
        // Pre-register grammar processor
        instances.put(GrammarProcessor.class, new JVoiceXmlGrammarProcessor());
        // Pre-register data model
        instances.put(DataModel.class, new SimpleDataModel());
        // Pre-register dialog factory
        instances.put(DialogFactory.class, new JVoiceXmlDialogFactory());
        // Register default profile
        profiles.put("default", new MinimalProfile());
    }

    public void setDocumentServer(DocumentServer server) {
        instances.put(DocumentServer.class, server);
    }

    @Override
    public <T> Collection<T> loadObjects(Class<T> type, String name) throws ConfigurationException {
        if (Profile.class.equals(type) && "profile".equals(name)) {
            java.util.ArrayList<Profile> list = new java.util.ArrayList<>(profiles.values());
            @SuppressWarnings("unchecked")
            java.util.Collection<T> result = (java.util.Collection<T>) list;
            return result;
        }
        if (DataModel.class.equals(type) && "datamodel".equals(name)) {
            @SuppressWarnings("unchecked")
            T instance = (T) instances.get(DataModel.class);
            if (instance != null) {
                return java.util.Collections.singletonList(instance);
            }
        }
        return Collections.emptyList();
    }

    @Override
    public <T> T loadObject(Class<T> type, String name) throws ConfigurationException {
        @SuppressWarnings("unchecked")
        T instance = (T) instances.get(type);
        return instance;
    }

    @Override
    public <T> T loadObject(Class<T> type) throws ConfigurationException {
        @SuppressWarnings("unchecked")
        T instance = (T) instances.get(type);
        
        // Special case: create a default DocumentServer if not registered
        if (instance == null && DocumentServer.class.equals(type)) {
            try {
                org.jvoicexml.documentserver.JVoiceXmlDocumentServer server = new org.jvoicexml.documentserver.JVoiceXmlDocumentServer();
                server.addSchemeStrategy(new FileSchemeStrategy());
                instances.put(type, server);
                return type.cast(server);
            } catch (Exception e) {
                throw new ConfigurationException("Failed to create document server", e);
            }
        }
        
        return instance;
    }
}
