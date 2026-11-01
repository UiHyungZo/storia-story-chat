"use server";

import { revalidatePath } from "next/cache";
import { adminFetch } from "@/lib/backend";

export type DecisionState = { error?: string; done?: boolean } | undefined;

async function errorMessage(res: Response): Promise<string> {
  const body = (await res.json().catch(() => null)) as { message?: string } | null;
  return body?.message ?? `요청에 실패했습니다. (HTTP ${res.status})`;
}

/** 관리자가 diff를 확인하고 체크박스(confirm)를 명시적으로 선택한 경우에만 승인 요청을 보낸다. */
export async function approveProposalAction(
  id: string,
  _prev: DecisionState,
  formData: FormData,
): Promise<DecisionState> {
  if (formData.get("confirm") !== "on") {
    return { error: "변경 내용을 확인했음을 체크해야 승인할 수 있습니다." };
  }

  const res = await adminFetch(`/api/admin/ai/proposals/${encodeURIComponent(id)}/approve`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ confirm: true }),
  });
  // 409(오래된 제안/이미 처리됨)도 상태가 바뀌었으므로 화면을 갱신한다.
  revalidatePath(`/agent/proposals/${id}`);
  revalidatePath("/agent/proposals");
  revalidatePath("/characters");
  if (!res.ok) {
    return { error: await errorMessage(res) };
  }
  return { done: true };
}

export async function rejectProposalAction(id: string): Promise<DecisionState> {
  const res = await adminFetch(`/api/admin/ai/proposals/${encodeURIComponent(id)}/reject`, { method: "POST" });
  revalidatePath(`/agent/proposals/${id}`);
  revalidatePath("/agent/proposals");
  if (!res.ok) {
    return { error: await errorMessage(res) };
  }
  return { done: true };
}
