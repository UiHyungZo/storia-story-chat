import { apiGet, apiPost } from "./client";
import { Message, MessageRole } from "../types";

type MessageResponseDto = {
  id: number;
  role: "USER" | "ASSISTANT";
  content: string;
  createdAt: string;
};

function fromDto(dto: MessageResponseDto): Message {
  return {
    id: dto.id,
    role: dto.role.toLowerCase() as MessageRole,
    content: dto.content,
    createdAt: dto.createdAt,
  };
}

type MessageExchangeDto = {
  userMessage: MessageResponseDto;
  assistantMessage: MessageResponseDto;
};

export async function fetchMessages(characterId: number): Promise<Message[]> {
  const dtos = await apiGet<MessageResponseDto[]>(`/api/conversations/${characterId}/messages`);
  return dtos.map(fromDto);
}

/**
 * REST fallback path — waits for the full Gemini reply (no streaming) and returns both
 * the saved user message and the generated assistant reply in one round trip.
 */
export async function postMessage(
  characterId: number,
  content: string
): Promise<{ userMessage: Message; assistantMessage: Message }> {
  const dto = await apiPost<MessageExchangeDto>(`/api/conversations/${characterId}/messages`, {
    content,
  });
  return { userMessage: fromDto(dto.userMessage), assistantMessage: fromDto(dto.assistantMessage) };
}
