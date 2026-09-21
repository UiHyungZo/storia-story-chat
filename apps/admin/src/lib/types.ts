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
