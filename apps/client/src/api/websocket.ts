import { Client, IMessage } from "@stomp/stompjs";
import { API_BASE_URL } from "./config";
import { getDeviceId } from "./deviceId";

const CONNECT_TIMEOUT_MS = 4000;

type StreamEventDto = {
  type: "CHUNK" | "DONE" | "ERROR";
  messageId: number | null;
  content: string | null;
  createdAt: string | null;
};

export type StreamHandlers = {
  onChunk: (content: string) => void;
  onDone: (message: { id: number; content: string; createdAt: string }) => void;
  onError: (message: string) => void;
};

const clients = new Map<number, Client>();

function wsUrl(): string {
  return `${API_BASE_URL.replace(/^http/, "ws")}/ws`;
}

export async function connectConversationSocket(
  characterId: number,
  handlers: StreamHandlers
): Promise<void> {
  const existing = clients.get(characterId);
  if (existing?.connected) return;

  const deviceId = await getDeviceId();

  await new Promise<void>((resolve, reject) => {
    const client = new Client({
      brokerURL: wsUrl(),
      connectHeaders: { "X-Device-Id": deviceId },
      reconnectDelay: 0,
    });

    const timeout = setTimeout(() => {
      client.deactivate();
      reject(new Error("WebSocket connect timeout"));
    }, CONNECT_TIMEOUT_MS);

    client.onConnect = () => {
      clearTimeout(timeout);
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
      clients.set(characterId, client);
      resolve();
    };

    client.onStompError = (frame) => {
      clearTimeout(timeout);
      reject(new Error(frame.headers?.message ?? "STOMP error"));
    };

    client.onWebSocketError = () => {
      clearTimeout(timeout);
      reject(new Error("WebSocket error"));
    };

    client.activate();
  });
}

export async function sendConversationMessage(characterId: number, content: string): Promise<boolean> {
  const client = clients.get(characterId);
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
  const client = clients.get(characterId);
  if (client) {
    client.deactivate();
    clients.delete(characterId);
  }
}
