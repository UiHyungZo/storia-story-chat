export type Character = {
  id: number;
  name: string;
  concept: string;
  ttsVoiceId: string | null;
};

export type MessageRole = "user" | "assistant";

export type Message = {
  id: number;
  role: MessageRole;
  content: string;
  createdAt: string;
};

export type RootStackParamList = {
  CharacterList: undefined;
  ChatRoom: { characterId: number };
};
