import { Client, IMessage, ReconnectionTimeMode } from "@stomp/stompjs";
import { API_BASE_URL } from "./config";
import { getDeviceId } from "./deviceId";

const CONNECT_TIMEOUT_MS = 4000;
const RECONNECT_DELAY_MS = 2000;
const MAX_RECONNECT_DELAY_MS = 30000;

type StreamEventDto = {
  type: "CHUNK" | "DONE" | "ERROR";
  messageId: number | null;
  content: string | null;
  createdAt: string | null;
};

export type ConnectionState = "connected" | "reconnecting";

export type StreamHandlers = {
  onChunk: (content: string) => void;
  onDone: (message: { id: number; content: string; createdAt: string }) => void;
  onError: (message: string) => void;
  /** Fires after the initial connect too (as "connected"), then on every drop/recover. */
  onConnectionStateChange?: (state: ConnectionState) => void;
};

type Entry = {
  client: Client;
  intentionalDisconnect: boolean;
};

const entries = new Map<number, Entry>();

function wsUrl(): string {
  return `${API_BASE_URL.replace(/^http/, "ws")}/ws`;
}

function subscribe(client: Client, characterId: number, handlers: StreamHandlers): void {
  client.subscribe(`/topic/conversation/${characterId}`, (message: IMessage) => {
    const event = JSON.parse(message.body) as StreamEventDto;
    if (event.type === "CHUNK" && event.content) {
      handlers.onChunk(event.content);
    } else if (event.type === "DONE" && event.messageId != null && event.content != null && event.createdAt) {
      handlers.onDone({ id: event.messageId, content: event.content, createdAt: event.createdAt });
    } else if (event.type === "ERROR") {
      handlers.onError(event.content ?? "Unknown error");
    }
  });
}

/**
 * Connects once and resolves/rejects on that first attempt only — callers use this
 * to decide ws-vs-rest for the screen session, same as before. Past that point the
 * STOMP client keeps itself alive: on an unexpected drop it retries with exponential
 * backoff (reconnectDelay/maxReconnectDelay/reconnectTimeMode below) and this module
 * re-subscribes on every reconnect, since STOMP subscriptions don't survive a drop.
 * `onConnectionStateChange` reports "reconnecting"/"connected" transitions so the UI
 * can show a banner, and `isSocketConnected` lets callers check live status per send.
 */
export async function connectConversationSocket(
  characterId: number,
  handlers: StreamHandlers
): Promise<void> {
  const existing = entries.get(characterId);
  if (existing?.client.connected) return;
  if (existing) {
    existing.intentionalDisconnect = true;
    existing.client.deactivate();
    entries.delete(characterId);
  }

  const deviceId = await getDeviceId();

  await new Promise<void>((resolve, reject) => {
    let settled = false;

    const client = new Client({
      brokerURL: wsUrl(),
      connectHeaders: { "X-Device-Id": deviceId },
      reconnectDelay: RECONNECT_DELAY_MS,
      maxReconnectDelay: MAX_RECONNECT_DELAY_MS,
      reconnectTimeMode: ReconnectionTimeMode.EXPONENTIAL,
    });

    const entry: Entry = { client, intentionalDisconnect: false };

    const timeout = setTimeout(() => {
      if (settled) return;
      settled = true;
      entry.intentionalDisconnect = true;
      client.deactivate();
      entries.delete(characterId);
      reject(new Error("WebSocket connect timeout"));
    }, CONNECT_TIMEOUT_MS);

    client.onConnect = () => {
      subscribe(client, characterId, handlers);
      handlers.onConnectionStateChange?.("connected");
      if (!settled) {
        settled = true;
        clearTimeout(timeout);
        resolve();
      }
    };

    client.onWebSocketClose = () => {
      // Before the first successful connect, onStompError/onWebSocketError/timeout
      // already handle rejection — this is only for drops after we were connected.
      if (settled && !entry.intentionalDisconnect) {
        handlers.onConnectionStateChange?.("reconnecting");
      }
    };

    client.onStompError = (frame) => {
      if (!settled) {
        settled = true;
        clearTimeout(timeout);
        entry.intentionalDisconnect = true;
        client.deactivate();
        entries.delete(characterId);
        reject(new Error(frame.headers?.message ?? "STOMP error"));
      }
    };

    client.onWebSocketError = () => {
      if (!settled) {
        settled = true;
        clearTimeout(timeout);
        entry.intentionalDisconnect = true;
        client.deactivate();
        entries.delete(characterId);
        reject(new Error("WebSocket error"));
      }
    };

    entries.set(characterId, entry);
    client.activate();
  });
}

export function isSocketConnected(characterId: number): boolean {
  return entries.get(characterId)?.client.connected ?? false;
}

export async function sendConversationMessage(characterId: number, content: string): Promise<boolean> {
  const client = entries.get(characterId)?.client;
  if (!client?.connected) return false;

  const deviceId = await getDeviceId();
  client.publish({
    destination: `/app/conversation/${characterId}/send`,
    headers: { "X-Device-Id": deviceId },
    body: JSON.stringify({ content }),
  });
  return true;
}

export function disconnectConversationSocket(characterId: number): void {
  const entry = entries.get(characterId);
  if (entry) {
    entry.intentionalDisconnect = true;
    entry.client.deactivate();
    entries.delete(characterId);
  }
}
