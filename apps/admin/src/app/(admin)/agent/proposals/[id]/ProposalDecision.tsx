"use client";

import { useActionState } from "react";
import { approveProposalAction, rejectProposalAction, type DecisionState } from "./actions";

export default function ProposalDecision({ id, disabled }: { id: string; disabled: boolean }) {
  const [approveState, approveAction, approving] = useActionState<DecisionState, FormData>(
    approveProposalAction.bind(null, id),
    undefined,
  );
  const [rejectState, rejectAction, rejecting] = useActionState<DecisionState, FormData>(
    () => rejectProposalAction(id),
    undefined,
  );

  return (
    <div className="space-y-4 rounded-lg border border-neutral-200 bg-white p-4">
      <form action={approveAction} className="space-y-3">
        <label className="flex items-start gap-2 text-sm text-neutral-700">
          <input type="checkbox" name="confirm" className="mt-1" disabled={disabled} />
          <span>위 변경 내용을 확인했으며, 이 캐릭터 설정에 실제로 반영하는 데 동의합니다.</span>
        </label>
        {approveState?.error && <p className="text-sm text-red-600">{approveState.error}</p>}
        <button
          type="submit"
          disabled={disabled || approving || rejecting}
          className="rounded-md bg-neutral-900 px-3 py-2 text-sm font-medium text-white disabled:opacity-50"
        >
          {approving ? "적용 중…" : "승인 및 적용"}
        </button>
      </form>
      <form action={rejectAction}>
        {rejectState?.error && <p className="mb-2 text-sm text-red-600">{rejectState.error}</p>}
        <button
          type="submit"
          disabled={disabled || approving || rejecting}
          className="rounded-md border border-neutral-300 px-3 py-2 text-sm text-neutral-700 disabled:opacity-50"
        >
          {rejecting ? "처리 중…" : "거절"}
        </button>
      </form>
    </div>
  );
}
