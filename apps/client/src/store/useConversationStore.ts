import { create } from "zustand";
import { notifyNewMessage } from "../../modules/storia-native";
import { fetchMessages, postMessage } from "../api/conversations";
import {
  connectConversationSocket,
  disconnectConversationSocket,
  isSocketConnected,
  sendConversationMessage,
} from "../api/websocket";
import { messagesCacheKey, readCache, writeCache } from "../storage/cache";
import { useCharacterStore } from "./useCharacterStore";
import { Message } from "../types";

function notifyAssistantReply(characterId: number, content: string): void {
  const name = useCharacterStore.getState().getCharacterById(characterId)?.name ?? "Storia";
  notifyNewMessage(name, content);
}

type Transport = "ws" | "rest";
type ConnectionStatus = "connected" | "reconnecting";

type ConversationStore = {
  messagesByCharacterId: Record<number, Message[]>;
  streamingByCharacterId: Record<number, string | undefined>;
  transportByCharacterId: Record<number, Transport | undefined>;
  connectionStatusByCharacterId: Record<number, ConnectionStatus | undefined>;
  isLoading: boolean;
  error: string | null;
  getMessages: (characterId: number) => Message[];
  getStreamingContent: (characterId: number) => string | undefined;
  getConnectionStatus: (characterId: number) => ConnectionStatus | undefined;
  loadMessages: (characterId: number) => Promise<void>;
  disconnect: (characterId: number) => void;
  sendMessage: (characterId: number, content: string) => Promise<void>;
  /**
   * Always goes over REST regardless of ws availability, and returns the
   * assistant reply — used by the voice call flow (useVoiceCallStore), which
   * needs a deterministic messageId to fetch TTS audio for, rather than the
   * WS path's fire-and-forget publish (sendMessage resolves before the
   * streamed reply arrives when using WS).
   */
  sendMessageViaRest: (characterId: number, content: string) => Promise<Message>;
};

let nextLocalId = -1;

export const useConversationStore = create<ConversationStore>((set, get) => {
  const sendViaRest = async (characterId: number, content: string): Promise<Message> => {
    const { userMessage, assistantMessage } = await postMessage(characterId, content);
    const messages = [...(get().messagesByCharacterId[characterId] ?? []), userMessage, assistantMessage];
    set((state) => ({
      messagesByCharacterId: { ...state.messagesByCharacterId, [characterId]: messages },
    }));
    writeCache(messagesCacheKey(characterId), messages);
    notifyAssistantReply(characterId, assistantMessage.content);
    return assistantMessage;
  };

  return {
  messagesByCharacterId: {},
  streamingByCharacterId: {},
  transportByCharacterId: {},
  connectionStatusByCharacterId: {},
  isLoading: false,
  error: null,
  getMessages: (characterId) => get().messagesByCharacterId[characterId] ?? [],
  getStreamingContent: (characterId) => get().streamingByCharacterId[characterId],
  getConnectionStatus: (characterId) => get().connectionStatusByCharacterId[characterId],
  loadMessages: async (characterId) => {
    if (!get().messagesByCharacterId[characterId]) {
      const cached = await readCache<Message[]>(messagesCacheKey(characterId));
      // Guard against a fetch that already landed while we were reading the cache.
      if (cached && !get().messagesByCharacterId[characterId]) {
        set((state) => ({
          messagesByCharacterId: { ...state.messagesByCharacterId, [characterId]: cached },
        }));
      }
    }

    set({ isLoading: true, error: null });
    try {
      const messages = await fetchMessages(characterId);
      set((state) => ({
        messagesByCharacterId: { ...state.messagesByCharacterId, [characterId]: messages },
        isLoading: false,
      }));
      writeCache(messagesCacheKey(characterId), messages);
    } catch (error) {
      set({ error: error instanceof Error ? error.message : String(error), isLoading: false });
    }
  },
  disconnect: (characterId) => {
    disconnectConversationSocket(characterId);
    set((state) => ({
      connectionStatusByCharacterId: { ...state.connectionStatusByCharacterId, [characterId]: undefined },
      // Re-decide ws-vs-rest on the next screen visit instead of sticking with
      // whatever this session landed on for the app's whole lifetime.
      transportByCharacterId: { ...state.transportByCharacterId, [characterId]: undefined },
    }));
  },
  sendMessage: async (characterId, content) => {
    if (!get().transportByCharacterId[characterId]) {
      let transport: Transport;
      try {
        await connectConversationSocket(characterId, {
          onChunk: (chunk) =>
            set((state) => ({
              streamingByCharacterId: {
                ...state.streamingByCharacterId,
                [characterId]: (state.streamingByCharacterId[characterId] ?? "") + chunk,
              },
            })),
          onDone: (message) => {
            const assistantMessage: Message = {
              id: message.id,
              role: "assistant",
              content: message.content,
              createdAt: message.createdAt,
            };
            const messages = [...(get().messagesByCharacterId[characterId] ?? []), assistantMessage];
            set((state) => ({
              messagesByCharacterId: { ...state.messagesByCharacterId, [characterId]: messages },
              streamingByCharacterId: { ...state.streamingByCharacterId, [characterId]: undefined },
            }));
            writeCache(messagesCacheKey(characterId), messages);
            notifyAssistantReply(characterId, assistantMessage.content);
          },
          onError: (message) =>
            set((state) => ({
              error: message,
              streamingByCharacterId: { ...state.streamingByCharacterId, [characterId]: undefined },
            })),
          onConnectionStateChange: (status) =>
            set((state) => ({
              connectionStatusByCharacterId: { ...state.connectionStatusByCharacterId, [characterId]: status },
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

    // Re-check live socket state on every send rather than trusting the cached
    // transport value — a WS session that dropped mid-stream and is now
    // reconnecting in the background should fall back to REST per-message until
    // it's actually back, then resume streaming on its own once it reconnects.
    const useWs = get().transportByCharacterId[characterId] === "ws" && isSocketConnected(characterId);

    if (useWs) {
      const localId = nextLocalId--;
      const localMessage: Message = {
        id: localId,
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

      // Socket dropped between the check above and the publish call — drop the
      // optimistic bubble and fall through to REST so it isn't rendered twice
      // once the REST call returns its own copy of the user message.
      set((state) => ({
        messagesByCharacterId: {
          ...state.messagesByCharacterId,
          [characterId]: (state.messagesByCharacterId[characterId] ?? []).filter((m) => m.id !== localId),
        },
      }));
    }

    await sendViaRest(characterId, content);
  },
  sendMessageViaRest: sendViaRest,
  };
});
