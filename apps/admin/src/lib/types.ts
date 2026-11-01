export type AdminCharacter = {
  id: number;
  name: string;
  concept: string;
  systemPrompt: string;
  ttsVoiceId: string | null;
  createdAt: string;
};

export type AdminConversationSummary = {
  id: number;
  userId: number;
  deviceId: string;
  characterId: number;
  characterName: string;
  createdAt: string;
};

export type Message = {
  id: number;
  role: "USER" | "ASSISTANT";
  content: string;
  createdAt: string;
};

export type AdminVoiceTurn = {
  turnId: string;
  deviceId: string;
  characterId: number;
  status: "recording" | "processing" | "done" | "error";
  createdAt: string;
  assistantMessageId: number | null;
  errorMessage: string | null;
};

export type PageMeta = {
  size: number;
  number: number;
  totalElements: number;
  totalPages: number;
};

export type PagedResponse<T> = {
  content: T[];
  page: PageMeta;
};

export type AgentToolCall = {
  name: string;
  arguments: string;
  ok: boolean;
  result: string;
};

export type AgentResponse = {
  answer: string;
  toolCalls: AgentToolCall[];
  stopReason: "completed" | "max_steps" | "timeout" | "no_response";
  knowledgeSources: string[];
  knowledgeStatus: "OK" | "NO_EVIDENCE" | "INDEX_EMPTY" | "ERROR" | null;
  evidenceNotice: string | null;
};

export type KnowledgeHit = {
  rank: number;
  chunkId: string;
  docId: string;
  source: string;
  title: string;
  heading: string;
  text: string;
  score: number;
};

export type KnowledgeIndexInfo = {
  provider: string;
  semantic: boolean;
  documents: number;
  chunks: number;
  builtAt: string;
  missingSources: string[];
};

export type KnowledgeSearchResult = {
  status: "OK" | "NO_EVIDENCE" | "INDEX_EMPTY";
  message: string | null;
  results: KnowledgeHit[];
  index: KnowledgeIndexInfo | null;
  tookMs: number;
};

export type ProposalFields = {
  concept: string | null;
  systemPrompt: string | null;
  ttsVoiceId: string | null;
};

export type AdminProposal = {
  id: string;
  characterId: number;
  characterName: string;
  status: "PENDING" | "APPROVED" | "REJECTED" | "STALE";
  reason: string;
  createdAt: string;
  decidedAt: string | null;
  stale: boolean;
  changedFields: Array<keyof ProposalFields>;
  current: ProposalFields;
  proposed: ProposalFields;
};

export type AgentToolStats = { name: string; calls: number; errors: number; avgDurationMs: number };

export type AgentRunRecord = {
  runId: string;
  startedAt: string;
  durationMs: number;
  stopReason: string;
  errorType: string | null;
  modelCalls: number;
  modelLatenciesMs: number[];
  promptTokens: number | null;
  outputTokens: number | null;
  totalTokens: number | null;
  tools: Array<{ name: string; ok: boolean; errorCode: string | null; durationMs: number }>;
};

export type AgentObservability = {
  summary: {
    runs: number;
    maxRecords: number;
    retentionHours: number;
    oldestRunAt: string | null;
    byStopReason: Record<string, number>;
    byErrorType: Record<string, number>;
    avgDurationMs: number | null;
    p95DurationMs: number | null;
    avgModelLatencyMs: number | null;
    totalTokens: number | null;
    tools: AgentToolStats[];
  };
  recentRuns: AgentRunRecord[];
  knowledge: {
    searches: number;
    byStatus: Record<string, number>;
    avgLatencyMs: number | null;
    p95LatencyMs: number | null;
    queryCharsEmbedded: number;
    indexCharsEmbedded: number;
    rebuilds: number;
  };
};
