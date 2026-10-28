"use server";

import { revalidatePath } from "next/cache";
import { adminFetch } from "@/lib/backend";
import type { KnowledgeIndexInfo, KnowledgeSearchResult } from "@/lib/types";

export type KnowledgeState =
  | { query?: string; result?: KnowledgeSearchResult; index?: KnowledgeIndexInfo; error?: string }
  | undefined;

async function errorMessage(res: Response): Promise<string> {
  const body = (await res.json().catch(() => null)) as { message?: string } | null;
  return body?.message ?? `요청에 실패했습니다. (HTTP ${res.status})`;
}

/** LLM을 거치지 않는 직접 검색. 인증은 adminFetch가 처리한다. */
export async function searchKnowledgeAction(_prev: KnowledgeState, formData: FormData): Promise<KnowledgeState> {
  const query = String(formData.get("query") ?? "").trim();
  if (!query) return { error: "검색어를 입력하세요." };
  const topK = Number(formData.get("topK") ?? 3);

  const res = await adminFetch("/api/admin/ai/knowledge/search", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ query, topK }),
  });
  if (!res.ok) return { query, error: await errorMessage(res) };
  return { query, result: (await res.json()) as KnowledgeSearchResult };
}

export async function reindexKnowledgeAction(_prev: KnowledgeState): Promise<KnowledgeState> {
  const res = await adminFetch("/api/admin/ai/knowledge/reindex", { method: "POST" });
  revalidatePath("/agent/knowledge");
  if (!res.ok) return { error: await errorMessage(res) };
  return { index: (await res.json()) as KnowledgeIndexInfo };
}
