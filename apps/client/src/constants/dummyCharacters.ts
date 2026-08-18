import { Character, Message } from "../types";

export const DUMMY_CHARACTERS: Character[] = [
  {
    id: "aria",
    name: "아리아 (Aria)",
    concept: "우주선 항법 AI가 인격을 얻어가는 SF 세계관",
    avatarColor: "#3B82F6",
    lastMessagePreview: "항로 계산이 끝났어요. 다음 목적지는 어디로 할까요?",
    lastMessageAt: "2026-08-17T09:12:00.000Z",
  },
  {
    id: "ren",
    name: "렌 (Ren)",
    concept: "폐업 위기의 작은 서점을 운영하는 인물",
    avatarColor: "#92400E",
    lastMessagePreview: "이 책 정말 좋아하실 것 같은데, 한번 읽어보실래요?",
    lastMessageAt: "2026-08-16T21:40:00.000Z",
  },
  {
    id: "noah",
    name: "노아 (Noah)",
    concept: "탐정 사무소 조수, 옴니버스 미스터리",
    avatarColor: "#57534E",
    lastMessagePreview: "이 사건, 생각보다 단순하지 않은데요.",
    lastMessageAt: "2026-08-15T14:05:00.000Z",
  },
];

export const DUMMY_MESSAGES: Record<string, Message[]> = {
  aria: [
    {
      id: "aria-1",
      conversationId: "aria",
      role: "assistant",
      content: "항법 시스템 아리아입니다. 무엇을 도와드릴까요?",
      createdAt: "2026-08-17T09:10:00.000Z",
    },
    {
      id: "aria-2",
      conversationId: "aria",
      role: "user",
      content: "다음 목적지 추천해줘.",
      createdAt: "2026-08-17T09:11:00.000Z",
    },
    {
      id: "aria-3",
      conversationId: "aria",
      role: "assistant",
      content: "항로 계산이 끝났어요. 다음 목적지는 어디로 할까요?",
      createdAt: "2026-08-17T09:12:00.000Z",
    },
  ],
  ren: [
    {
      id: "ren-1",
      conversationId: "ren",
      role: "assistant",
      content: "어서오세요, 오늘은 어떤 이야기가 필요하세요?",
      createdAt: "2026-08-16T21:38:00.000Z",
    },
    {
      id: "ren-2",
      conversationId: "ren",
      role: "user",
      content: "요즘 마음이 좀 복잡해서요.",
      createdAt: "2026-08-16T21:39:00.000Z",
    },
    {
      id: "ren-3",
      conversationId: "ren",
      role: "assistant",
      content: "이 책 정말 좋아하실 것 같은데, 한번 읽어보실래요?",
      createdAt: "2026-08-16T21:40:00.000Z",
    },
  ],
  noah: [
    {
      id: "noah-1",
      conversationId: "noah",
      role: "assistant",
      content: "탐정 사무소입니다. 어떤 사건을 가져오셨나요?",
      createdAt: "2026-08-15T14:03:00.000Z",
    },
    {
      id: "noah-2",
      conversationId: "noah",
      role: "user",
      content: "옆집에서 이상한 소리가 들려요.",
      createdAt: "2026-08-15T14:04:00.000Z",
    },
    {
      id: "noah-3",
      conversationId: "noah",
      role: "assistant",
      content: "이 사건, 생각보다 단순하지 않은데요.",
      createdAt: "2026-08-15T14:05:00.000Z",
    },
  ],
};
