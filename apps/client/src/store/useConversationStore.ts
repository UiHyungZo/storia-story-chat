import { create } from "zustand";
import { fetchMessages, postMessage } from "../api/conversations";
import {
  connectConversationSocket,
  disconnectConversationSocket,
  sendConversationMessage,
} from "../api/websocket";
import { Message } from "../types";

type Transport = "connecting" | "ws" | "rest";

type ConversationStore = {
  messagesByCharacterId: Record<number, Message[]>;
  streamingByCharacterId: Record<number, string | undefined>;
  transportByCharacterId: Record<number, Transport | undefined>;
  isLoading: boolean;
  error: string | null;
  getMessages: (characterId: number) => Message[];
  getStreamingContent: (characterId: number) => string | undefined;
  loadMessages: (characterId: number) => Promise<void>;
  disconnect: (characterId: number) => void;
  sendMessage: (characterId: number, content: string) => Promise<void>;
};

let nextLocalId = -1;

export const useConversationStore = create<ConversationStore>((set, get) => ({
  messagesByCharacterId: {},
  streamingByCharacterId: {},
  transportByCharacterId: {},
  isLoading: false,
  error: null,
  getMessages: (characterId) => get().messagesByCharacterId[characterId] ?? [],
  getStreamingContent: (characterId) => get().streamingByCharacterId[characterId],
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
  disconnect: (characterId) => {
    disconnectConversationSocket(characterId);
    set((state) => ({
      transportByCharacterId: { ...state.transportByCharacterId, [characterId]: undefined },
    }));
  },
  sendMessage: async (characterId, content) => {
    let transport = get().transportByCharacterId[characterId];

    if (!transport) {
      set((state) => ({
        transportByCharacterId: { ...state.transportByCharacterId, [characterId]: "connecting" },
      }));
      try {
        await connectConversationSocket(characterId, {
          onChunk: (chunk) =>
            set((state) => ({
              streamingByCharacterId: {
                ...state.streamingByCharacterId,
                [characterId]: (state.streamingByCharacterId[characterId] ?? "") + chunk,
              },
            })),
          onDone: (message) =>
            set((state) => ({
              messagesByCharacterId: {
                ...state.messagesByCharacterId,
                [characterId]: [
                  ...(state.messagesByCharacterId[characterId] ?? []),
                  { id: message.id, role: "assistant", content: message.content, createdAt: message.createdAt },
                ],
              },
              streamingByCharacterId: { ...state.streamingByCharacterId, [characterId]: undefined },
            })),
          onError: (message) =>
            set((state) => ({
              error: message,
              streamingByCharacterId: { ...state.streamingByCharacterId, [characterId]: undefined },
            })),
        });
        transport = "ws";
      } catch {
        transport = "rest";
      }
      set((state) => ({
        transportByCharacterId: { ...state.transportByCharacterId, [characterId]: transport },
      }));
    }

    if (transport === "ws") {
      const localMessage: Message = {
        id: nextLocalId--,
        role: "user",
        content,
        createdAt: new Date().toISOString(),
      };
      set((state) => ({
        messagesByCharacterId: {
          ...state.messagesByCharacterId,
          [characterId]: [...(state.messagesByCharacterId[characterId] ?? []), localMessage],
        },
      }));

      const sent = await sendConversationMessage(characterId, content);
      if (sent) return;

      // Socket dropped between connect and send — this one message falls back to REST.
      transport = "rest";
    }

    const { userMessage, assistantMessage } = await postMessage(characterId, content);
    set((state) => ({
      messagesByCharacterId: {
        ...state.messagesByCharacterId,
        [characterId]: [...(state.messagesByCharacterId[characterId] ?? []), userMessage, assistantMessage],
      },
    }));
  },
}));
