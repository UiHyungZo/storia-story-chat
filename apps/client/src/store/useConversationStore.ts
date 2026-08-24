import { create } from "zustand";
import { fetchMessages, postMessage } from "../api/conversations";
import { Message } from "../types";

type ConversationStore = {
  messagesByCharacterId: Record<number, Message[]>;
  isLoading: boolean;
  error: string | null;
  getMessages: (characterId: number) => Message[];
  loadMessages: (characterId: number) => Promise<void>;
  sendMessage: (characterId: number, content: string) => Promise<void>;
};

export const useConversationStore = create<ConversationStore>((set, get) => ({
  messagesByCharacterId: {},
  isLoading: false,
  error: null,
  getMessages: (characterId) => get().messagesByCharacterId[characterId] ?? [],
  loadMessages: async (characterId) => {
    set({ isLoading: true, error: null });
    try {
      const messages = await fetchMessages(characterId);
      set((state) => ({
        messagesByCharacterId: { ...state.messagesByCharacterId, [characterId]: messages },
        isLoading: false,
      }));
    } catch (error) {
      set({ error: error instanceof Error ? error.message : String(error), isLoading: false });
    }
  },
  sendMessage: async (characterId, content) => {
    const message = await postMessage(characterId, content);
    set((state) => ({
      messagesByCharacterId: {
        ...state.messagesByCharacterId,
        [characterId]: [...(state.messagesByCharacterId[characterId] ?? []), message],
      },
    }));
  },
}));
