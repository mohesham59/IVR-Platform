/**
 * Client API module for NexusIVR AI Engine REST services.
 * Connects React frontend components to backend Servlets at /api/v1/ai/*
 */

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/v1/ai';
const DEFAULT_TENANT_ID = '00000000-0000-0000-0000-000000000001';

export interface ProviderAttempt {
  provider: string;
  status: number;
  reason: string;
  cooldownSeconds?: number;
}

export interface ChatMessage {
  id: string;
  role: 'user' | 'ai';
  text: string;
  type?: 'text' | 'code' | 'flow-preview' | 'suggestion-list';
  extra?: any;
  ts: string;
}

export interface ChatApiResponse {
  sessionId: string;
  tenantId: string;
  replyMessage: string;
  role: string;
  turnNumber: number;
  tokensUsed: number;
  flowJson?: string;
  quotaWarnings?: Array<{ provider: string; model?: string; attempt: number }>;
  selectedProvider?: string;
  actualProviderUsed?: string;
  providerAttempts?: ProviderAttempt[];
}

export interface FlowApiResponse {
  id?: string;
  name?: string;
  description?: string;
  flowJson?: string;
  status?: string;
  nodes?: any[];
  edges?: any[];
  quotaWarnings?: Array<{ provider: string; model?: string; attempt: number }>;
  selectedProvider?: string;
  actualProviderUsed?: string;
  providerAttempts?: ProviderAttempt[];
}

export interface FlowImprovementApiResponse {
  suggestedFlowJson: string;
  improvementSummary: string;
  containmentScoreEstimate: number;
  quotaWarnings?: Array<{ provider: string; model?: string; attempt: number }>;
  selectedProvider?: string;
  actualProviderUsed?: string;
  fallbackUsed?: boolean;
  fallbackReason?: string;
  providerAttempts?: ProviderAttempt[];
}

export interface FlowValidationApiResponse {
  valid: boolean;
  issues: Array<{ severity: string; message: string; nodeId?: string }>;
}

export interface AnalyticsApiResponse {
  tenantId: string;
  totalSessions: number;
  activeSessions: number;
  totalMessages: number;
}

export interface TelephonyAnalytics {
  liveCalls: number;
  recentCalls: Array<{ caller: string; status: string; duration: string; scenario: string }>;
  callVolume: Array<{ time: string; inbound: number; outbound: number }>;
  callDist: Array<{ name: string; value: number; color: string }>;
}

export interface SummarizationApiResponse {
  summary: string;
  keyPoints: string[];
  sentimentLabel: string;
}

export interface SentimentApiResponse {
  sentiment: string;
  score: number;
  escalationRisk: string;
}

export interface CdrCall {
  uniqueId: string;
  caller: string;
  callee: string;
  start: string;
  answer: string;
  durationSec: number;
  billsec: number;
  disposition: string;
  status: string;
}

export interface CdrDayBucket {
  day: string;
  calls: number;
  answered: number;
  abandoned: number;
}

export interface CdrHourBucket {
  hour: number;
  calls: number;
}

export interface CdrSummary {
  totalCalls: number;
  answered: number;
  abandoned: number;
  answeredRate: number;
  abandonedRate: number;
  avgDurationSec: number;
  avgBillsec: number;
  daily: CdrDayBucket[];
  hourly: CdrHourBucket[];
}

async function request<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
  const url = `${API_BASE_URL}${endpoint}`;
  const activeProvider = localStorage.getItem('ai_provider') || 'gemini';
  const activeSessionId = localStorage.getItem('nexus_ai_session_id') || '';
  const headers = {
    'Content-Type': 'application/json',
    'X-Tenant-ID': DEFAULT_TENANT_ID,
    'X-AI-Provider': activeProvider,
    'X-Session-ID': activeSessionId,
    ...(options.headers || {}),
  };

  try {
    const res = await fetch(url, { ...options, headers });
    if (!res.ok) {
      const errorText = await res.text();
      if (res.status === 404) {
        console.error(`[aiApi] API endpoint not found (${res.status}). Backend context path may be misconfigured. Expected: /nexusivr-ai-engine${endpoint}. Check vite.config.ts proxy rewrite.`);
      }
      throw new Error(`API Error (${res.status}): ${errorText || res.statusText}`);
    }
    return (await res.json()) as T;
  } catch (err: any) {
    const msg = err.message || err;
    if (msg.includes('Failed to fetch') || msg.includes('NetworkError') || msg.includes('ECONNREFUSED')) {
      console.error(`[aiApi] Backend unreachable at ${url}. Check vite.config.ts proxy settings and ensure backend is running on port 8080.`);
    } else if (msg.includes('404')) {
      console.error(`[aiApi] ${msg}`);
    } else {
      console.warn(`[aiApi] API call to ${url} failed:`, msg);
    }
    throw err;
  }
}

export const aiApi = {
  /**
   * Set active AI provider (ollama vs groq)
   */
  async setProvider(provider: string): Promise<{ success: boolean; provider: string; model: string; available: boolean }> {
    localStorage.setItem('ai_provider', provider);
    return request<{ success: boolean; provider: string; model: string; available: boolean }>('/provider', {
      method: 'POST',
      body: JSON.stringify({ provider }),
    });
  },

  /**
   * Fetch active AI provider status
   */
  async fetchProvider(): Promise<{ provider: string; model: string; available: boolean }> {
    return request<{ provider: string; model: string; available: boolean }>('/provider', {
      method: 'GET',
    });
  },
  /**
   * Send a chat message turn to the AI assistant.
   * Pass flowContext (serialized canvas nodes+edges JSON) so the backend
   * always answers from the same flow the preview shows.
   */
  async sendMessage(userMessage: string, sessionId?: string, channel = 'CHAT', flowContext?: string, snapshotId?: string, autoRefine?: boolean, options: any = {}): Promise<ChatApiResponse> {
    const headers: Record<string, string> = {};
    if (snapshotId) {
      headers['X-AI-Snapshot-ID'] = snapshotId;
    }
    return request<ChatApiResponse>('/chat', {
      method: 'POST',
      headers,
      ...options,
      body: JSON.stringify({ sessionId, userMessage, channel, flowContext, autoRefine }),
    });
  },

  /**
   * Fetch dynamically registered AI agents from the backend
   */
  async fetchAgents(): Promise<any[]> {
    return request<any[]>('/agents', {
      method: 'GET',
    });
  },

  /**
   * Generate an IVR flow structure from a business description prompt
   */
  async generateFlow(description: string, options: any = {}): Promise<FlowApiResponse> {
    return request<FlowApiResponse>('/flow/generate', {
      method: 'POST',
      ...options,
      body: JSON.stringify({ description }),
    });
  },

  /**
   * Request AI improvements for an existing IVR flow
   */
  async improveFlow(existingFlow: any, improvementGoals: string[], options: any = {}): Promise<FlowImprovementApiResponse> {
    return request<FlowImprovementApiResponse>('/flow/improve', {
      method: 'POST',
      ...options,
      body: JSON.stringify({ existingFlow, improvementGoals }),
    });
  },

  /**
   * Validate IVR flow structure and return quality/compliance issues
   */
  async validateFlow(flow: any): Promise<FlowValidationApiResponse> {
    return request<FlowValidationApiResponse>('/flow/validate', {
      method: 'POST',
      body: JSON.stringify({ flow }),
    });
  },

  /**
   * Fetch aggregate AI analytics for the current tenant
   */
  async fetchAnalytics(): Promise<AnalyticsApiResponse> {
    return request<AnalyticsApiResponse>('/analytics', {
      method: 'GET',
    });
  },

  /**
   * Fetch recent call records from the Asterisk CDR log (newest first)
   */
  async fetchCdrCalls(): Promise<CdrCall[]> {
    return request<CdrCall[]>('/cdr/calls', {
      method: 'GET',
    });
  },

  /**
   * Fetch aggregate CDR analytics (KPIs + daily/hourly series)
   */
  async fetchCdrSummary(): Promise<CdrSummary> {
    return request<CdrSummary>('/cdr/summary', {
      method: 'GET',
    });
  },

  /**
   * Fetch conversation history for a session
   */
  async fetchHistory(sessionId?: string): Promise<any> {
    const query = sessionId ? `?sessionId=${encodeURIComponent(sessionId)}` : '';
    return request<any>(`/history${query}`, {
      method: 'GET',
    });
  },

  /**
   * Summarize a conversation transcript
   */
  async summarizeConversation(messages: any[]): Promise<SummarizationApiResponse> {
    return request<SummarizationApiResponse>('/summarize', {
      method: 'POST',
      body: JSON.stringify({ messages }),
    });
  },

  /**
   * Analyze sentiment and escalation risk for text content
   */
  async analyzeSentiment(content: string): Promise<SentimentApiResponse> {
    return request<SentimentApiResponse>('/sentiment', {
      method: 'POST',
      body: JSON.stringify({ content }),
    });
  },

  /**
   * Execute an AI requested tool or function action
   */
  async executeFunction(functionName: string, parameters: any = {}): Promise<any> {
    return request<any>('/function-call', {
      method: 'POST',
      body: JSON.stringify({ functionName, parameters }),
    });
  },

  /**
   * Fetch active AI prompt templates
   */
  async fetchPrompts(): Promise<any> {
    return request<any>('/prompts', {
      method: 'GET',
    });
  },

  /**
   * Delete a conversation session and its messages
   */
  async deleteConversation(sessionId: string): Promise<{ success: boolean; sessionId: string }> {
    return request<{ success: boolean; sessionId: string }>(`/conversation/${encodeURIComponent(sessionId)}`, {
      method: 'DELETE',
    });
  },

  /**
   * Explicitly create a new chat session in the backend database
   */
  async createNewChat(title = 'New Chat'): Promise<{ sessionId: string; tenantId: string; status: string; customerIdentifier: string }> {
    return request<{ sessionId: string; tenantId: string; status: string; customerIdentifier: string }>('/new-chat', {
      method: 'POST',
      body: JSON.stringify({ title }),
    });
  },

  /**
   * Persist full conversation history to the backend database
   */
  async saveHistory(sessionId: string, messages: any[], title?: string): Promise<any> {
    return request<any>('/history', {
      method: 'POST',
      body: JSON.stringify({ sessionId, messages, title }),
    });
  },

  /**
   * Rename a chat conversation session title
   */
  async renameConversation(sessionId: string, newTitle: string): Promise<{ success: boolean; sessionId: string; title: string }> {
    return request<{ success: boolean; sessionId: string; title: string }>(`/history/${encodeURIComponent(sessionId)}`, {
      method: 'PATCH',
      body: JSON.stringify({ title: newTitle }),
    });
  },

  /**
   * Fetch list of supported providers and their models dynamically
   */
  async fetchProviders(): Promise<Record<string, string[]>> {
    const response = await request<{ success: boolean; providers: Array<{ name: string; enabled: boolean; model: string }> }>('/providers', {
      method: 'GET',
    });
    const result: Record<string, string[]> = {}
    if (response.providers && Array.isArray(response.providers)) {
      for (const p of response.providers) {
        if (p.name && !result[p.name]) {
          result[p.name] = p.model ? [p.model] : []
        }
      }
    }
    return result
  },

  /**
   * Request LLM-driven structured AI Suggestions for a flow
   */
  async getAiSuggestions(data: { flow: any; issues?: any[] }): Promise<{ suggestions: any[]; count: number }> {
    try {
      const flowPayload = typeof data.flow === 'string' ? JSON.parse(data.flow) : data.flow;
      return await request<{ suggestions: any[]; count: number }>('/flow/suggestions', {
        method: 'POST',
        body: JSON.stringify({
          flow: flowPayload,
          issues: data.issues || [],
        }),
      });
    } catch {
      return { suggestions: [], count: 0 };
    }
  },

  /**
   * Parse VoiceXML text into a Flow JSON
   */
  async importVxml(vxml: string): Promise<any> {
    return request<any>('/flow/parse', {
      method: 'POST',
      body: JSON.stringify({ vxml }),
    });
  },

  /**
   * Save a draft version of the flow to the backend.
   * Backend endpoint not yet implemented — stub returns success.
   */
  async saveDraft(
    data: { flowId: string; flowName: string; flowJson: string },
    _onRetry?: (attempt: number, maxAttempts: number, errorMsg: string) => void
  ): Promise<{ version: number; filename: string }> {
    return request<{ version: number; filename: string }>('/flow/draft', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  },

  /**
   * Publish the flow as a production VXML scenario.
   * Backend endpoint not yet implemented — stub returns success.
   */
  async publishFlow(data: { flowId: string; flowName: string; extension?: string; flowJson: string }): Promise<{
    filename: string;
    status: string;
    extensionRegistered: boolean;
    extensionMessage?: string;
    warning?: string;
    filePath: string;
    validationScore?: number;
  }> {
    return request<any>('/flow/publish', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  },

  /**
   * Export the flow as a VoiceXML string from the backend.
   */
  async exportVxml(flowJson: string): Promise<{ vxml: string }> {
    return request<{ vxml: string }>('/flow/export', {
      method: 'POST',
      body: JSON.stringify({ flowJson }),
    });
  },

  async cancelGeneration(sessionId: string): Promise<{ success: boolean }> {
    return request<{ success: boolean }>(`/generation/${encodeURIComponent(sessionId)}/cancel`, {
      method: 'POST',
    });
  },

  /**
   * Fetch telephony analytics for dashboards
   */
  async fetchTelephonyAnalytics(): Promise<TelephonyAnalytics> {
    return request<TelephonyAnalytics>('/telephony/analytics', {
      method: 'GET',
    });
  }
};
