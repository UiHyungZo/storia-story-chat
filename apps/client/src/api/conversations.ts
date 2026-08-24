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

export async function fetchMessages(characterId: number): Promise<Message[]> {
  const dtos = await apiGet<MessageResponseDto[]>(`/api/conversations/${characterId}/messages`);
  return dtos.map(fromDto);
}

export async function postMessage(characterId: number, content: string): Promise<Message> {
  const dto = await apiPost<MessageResponseDto>(`/api/conversations/${characterId}/messages`, {
    content,
  });
  return fromDto(dto);
}
