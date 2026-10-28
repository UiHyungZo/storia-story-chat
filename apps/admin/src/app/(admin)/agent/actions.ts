"use server";

import { adminFetch } from "@/lib/backend";
import type { AgentResponse } from "@/lib/types";

export type AgentState =
  | { question?: string; response?: AgentResponse; error?: string }
  | undefined;

const MAX_QUESTION_LENGTH = 1000;

export async function askAgentAction(_prev: AgentState, formData: FormData): Promise<AgentState> {
  const question = String(formData.get("question") ?? "").trim();
  if (!question) {
    return { error: "질문을 입력하세요." };
  }
  if (question.length > MAX_QUESTION_LENGTH) {
    return { question, error: `질문은 ${MAX_QUESTION_LENGTH}자 이하여야 합니다.` };
  }

  // 인증은 adminFetch가 처리한다(세션 없음/401이면 /login으로 redirect). 브라우저는 토큰을 보지 못한다.
  const res = await adminFetch("/api/admin/ai/agent/query", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ question }),
  });

  if (!res.ok) {
    const body = (await res.json().catch(() => null)) as { message?: string } | null;
    return { question, error: body?.message ?? `요청에 실패했습니다. (HTTP ${res.status})` };
  }

  return { question, response: (await res.json()) as AgentResponse };
}
