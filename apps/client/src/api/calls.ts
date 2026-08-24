import { apiGet, apiPost } from "./client";

export type CallTokenDto = {
  token: string;
  url: string;
  roomName: string;
};

/** 축소판 A안 — LiveKit room join token. 503이면 백엔드에 LiveKit 설정이 안 된 것. */
export function requestCallToken(characterId: number): Promise<CallTokenDto> {
  return apiPost<CallTokenDto>(`/api/calls/${characterId}/token`, {});
}

export type StartTurnDto = {
  turnId: string;
};

/** 로컬에서 마이크 트랙을 publish한 직후 호출 — 백엔드가 그 트랙의 Track Egress를 시작한다. */
export function startTurnEgress(
  characterId: number,
  roomName: string,
  trackSid: string
): Promise<StartTurnDto> {
  return apiPost<StartTurnDto>(`/api/calls/${characterId}/turns`, { roomName, trackSid });
}

export type TurnStatusDto = {
  status: "recording" | "processing" | "done" | "error";
  assistantMessageId: number | null;
  errorMessage: string | null;
};

export function getTurnStatus(turnId: string): Promise<TurnStatusDto> {
  return apiGet<TurnStatusDto>(`/api/calls/turns/${turnId}`);
}
