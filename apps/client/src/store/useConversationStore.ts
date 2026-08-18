import { create } from "zustand";
import { DUMMY_MESSAGES } from "../constants/dummyCharacters";
import { Message } from "../types";

type ConversationStore = {
  messagesByConversationId: Record<string, Message[]>;
  getMessages: (conversationId: string) => Message[];
  sendMessage: (conversationId: string, content: string) => void;
};

export const useConversationStore = create<ConversationStore>((set, get) => ({
  messagesByConversationId: DUMMY_MESSAGES,
  getMessages: (conversationId) => get().messagesByConversationId[conversationId] ?? [],
  sendMessage: (conversationId, content) => {
    const newMessage: Message = {
      id: `${conversationId}-${Date.now()}`,
      conversationId,
      role: "user",
      content,
      createdAt: new Date().toISOString(),
    };
    set((state) => ({
      messagesByConversationId: {
        ...state.messagesByConversationId,
        [conversationId]: [...(state.messagesByConversationId[conversationId] ?? []), newMessage],
      },
    }));
  },
}));
