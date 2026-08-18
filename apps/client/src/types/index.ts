export type Character = {
  id: string;
  name: string;
  concept: string;
  avatarColor: string;
  lastMessagePreview?: string;
  lastMessageAt?: string;
};

export type MessageRole = "user" | "assistant";

export type Message = {
  id: string;
  conversationId: string;
  role: MessageRole;
  content: string;
  createdAt: string;
};

export type RootStackParamList = {
  CharacterList: undefined;
  ChatRoom: { characterId: string };
};
