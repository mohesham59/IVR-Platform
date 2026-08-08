package com.nexusivr.ai.controller;

import com.nexusivr.ai.ai.FunctionExecutor;
import com.nexusivr.ai.ai.LlmClient;
import com.nexusivr.ai.ai.LlmProviderFactory;
import com.nexusivr.ai.ai.PromptBuilder;
import com.nexusivr.ai.dao.AiSessionDao;
import com.nexusivr.ai.dao.FlowDao;
import com.nexusivr.ai.dao.KnowledgeDocumentDao;
import com.nexusivr.ai.dao.MessageDao;
import com.nexusivr.ai.service.AiService;
import com.nexusivr.ai.service.AnalyticsService;
import com.nexusivr.ai.service.CdrService;
import com.nexusivr.ai.service.ChatService;
import com.nexusivr.ai.service.FlowService;
import com.nexusivr.ai.service.KnowledgeService;

/**
 * Global application registry providing singletons of services, DAOs, and AI components
 * for Jakarta Servlets running inside a web container.
 * Uses lazy initialization to prevent startup crashes if PostgreSQL is unavailable.
 */
public class ServiceRegistry {

    private static volatile AiSessionDao sessionDao;
    private static volatile MessageDao messageDao;
    private static volatile FlowDao flowDao;
    private static volatile KnowledgeDocumentDao knowledgeDao;

    private static volatile PromptBuilder promptBuilder;
    private static volatile FunctionExecutor functionExecutor;

    private static volatile AiService aiService;
    private static volatile ChatService chatService;
    private static volatile FlowService flowService;
    private static volatile AnalyticsService analyticsService;
    private static volatile CdrService cdrService;
    private static volatile KnowledgeService knowledgeService;
    private static volatile com.nexusivr.ai.service.FlowContextService flowContextService;

    private static volatile com.nexusivr.ai.ai.ProviderManager providerManager;
    private static volatile com.nexusivr.ai.service.FlowSnapshotService flowSnapshotService;
    private static volatile com.nexusivr.ai.service.UnifiedAiEngine unifiedAiEngine;
    private static volatile com.nexusivr.ai.service.AiOperationRouter aiOperationRouter;
    private static volatile com.nexusivr.ai.ai.agents.SpecializedAgentService specializedAgentService;

    private ServiceRegistry() {}

    public static synchronized AiSessionDao getSessionDao() {
        if (sessionDao == null) sessionDao = new AiSessionDao();
        return sessionDao;
    }

    public static synchronized MessageDao getMessageDao() {
        if (messageDao == null) messageDao = new MessageDao();
        return messageDao;
    }

    public static synchronized FlowDao getFlowDao() {
        if (flowDao == null) flowDao = new FlowDao();
        return flowDao;
    }

    public static synchronized KnowledgeDocumentDao getKnowledgeDao() {
        if (knowledgeDao == null) knowledgeDao = new KnowledgeDocumentDao();
        return knowledgeDao;
    }

    public static synchronized PromptBuilder getPromptBuilder() {
        if (promptBuilder == null) promptBuilder = new PromptBuilder();
        return promptBuilder;
    }

    public static synchronized LlmClient getLlmClient() {
        return com.nexusivr.ai.ai.LlmProviderFactory.getLlmClient();
    }


    public static synchronized FunctionExecutor getFunctionExecutor() {
        if (functionExecutor == null) functionExecutor = new FunctionExecutor();
        return functionExecutor;
    }

    public static synchronized AiService getAiService() {
        if (aiService == null) aiService = new AiService(getPromptBuilder(), getLlmClient(), getFunctionExecutor(), getProviderManager());
        return aiService;
    }

    public static AiService getAiService(String provider) {
        if (provider != null && !provider.isBlank()) {
            return new AiService(getPromptBuilder(), LlmProviderFactory.getLlmClient(provider), getFunctionExecutor(), getProviderManager());
        }
        return getAiService();
    }

    public static synchronized ChatService getChatService() {
        if (chatService == null) chatService = new ChatService(getSessionDao(), getMessageDao(), getAiService());
        return chatService;
    }

    public static ChatService getChatService(String provider) {
        if (provider != null && !provider.isBlank()) {
            return new ChatService(getSessionDao(), getMessageDao(), getAiService(provider));
        }
        return getChatService();
    }

    public static synchronized FlowService getFlowService() {
        if (flowService == null) flowService = new FlowService(getFlowDao(), getAiService());
        return flowService;
    }

    public static FlowService getFlowService(String provider) {
        if (provider != null && !provider.isBlank()) {
            return new FlowService(getFlowDao(), getAiService(provider));
        }
        return getFlowService();
    }

    public static synchronized AnalyticsService getAnalyticsService() {
        if (analyticsService == null) analyticsService = new AnalyticsService(getSessionDao(), getMessageDao());
        return analyticsService;
    }

    public static synchronized CdrService getCdrService() {
        if (cdrService == null) cdrService = new CdrService();
        return cdrService;
    }

    public static synchronized KnowledgeService getKnowledgeService() {
        if (knowledgeService == null) knowledgeService = new KnowledgeService(getKnowledgeDao());
        return knowledgeService;
    }

    public static synchronized com.nexusivr.ai.service.FlowContextService getFlowContextService() {
        if (flowContextService == null) flowContextService = new com.nexusivr.ai.service.FlowContextService();
        return flowContextService;
    }

    public static synchronized com.nexusivr.ai.ai.ProviderManager getProviderManager() {
        if (providerManager == null) providerManager = new com.nexusivr.ai.ai.ProviderManager();
        return providerManager;
    }

    public static synchronized com.nexusivr.ai.service.UnifiedAiEngine getUnifiedAiEngine() {
        if (unifiedAiEngine == null) {
            unifiedAiEngine = new com.nexusivr.ai.service.UnifiedAiEngine(
                    getProviderManager(),
                    getPromptBuilder(),
                    getFlowContextService()
            );
        }
        return unifiedAiEngine;
    }

    public static synchronized com.nexusivr.ai.ai.agents.SpecializedAgentService getSpecializedAgentService() {
        if (specializedAgentService == null) {
            specializedAgentService = new com.nexusivr.ai.ai.agents.SpecializedAgentService(getProviderManager());
        }
        return specializedAgentService;
    }

    public static synchronized com.nexusivr.ai.service.AiOperationRouter getAiOperationRouter() {
        if (aiOperationRouter == null) {
            aiOperationRouter = new com.nexusivr.ai.service.AiOperationRouter(getUnifiedAiEngine(), getChatService(), getSpecializedAgentService());
        }
        return aiOperationRouter;
    }

    public static synchronized com.nexusivr.ai.service.FlowSnapshotService getFlowSnapshotService() {
        if (flowSnapshotService == null) {
            flowSnapshotService = new com.nexusivr.ai.service.FlowSnapshotService();
        }
        return flowSnapshotService;
    }

    private static volatile com.nexusivr.ai.service.FlowPublishService flowPublishService;

    public static synchronized com.nexusivr.ai.service.FlowPublishService getFlowPublishService() {
        if (flowPublishService == null) {
            flowPublishService = new com.nexusivr.ai.service.FlowPublishService();
        }
        return flowPublishService;
    }

    private static volatile com.nexusivr.ai.service.FlowDraftService flowDraftService;

    public static synchronized com.nexusivr.ai.service.FlowDraftService getFlowDraftService() {
        if (flowDraftService == null) {
            flowDraftService = new com.nexusivr.ai.service.FlowDraftService();
        }
        return flowDraftService;
    }
}
