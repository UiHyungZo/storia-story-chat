import Link from "next/link";
import { notFound } from "next/navigation";
import { adminFetch } from "@/lib/backend";
import type { AdminProposal, ProposalFields } from "@/lib/types";
import ProposalDecision from "./ProposalDecision";

const FIELD_LABEL: Record<keyof ProposalFields, string> = {
  concept: "컨셉",
  systemPrompt: "시스템 프롬프트",
  ttsVoiceId: "TTS 보이스",
};

const STATUS_LABEL: Record<AdminProposal["status"], string> = {
  PENDING: "승인 대기",
  APPROVED: "승인됨 (적용 완료)",
  REJECTED: "거절됨",
  STALE: "오래됨 — 제안 이후 설정이 변경되어 적용 불가",
};

function show(value: string | null, cleared: boolean) {
  if (cleared) return "(값 해제)";
  return value === null || value === "" ? "(없음)" : value;
}

export default async function ProposalDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const res = await adminFetch(`/api/admin/ai/proposals/${encodeURIComponent(id)}`);
  if (res.status === 404) notFound();
  if (!res.ok) {
    return <p className="text-sm text-red-600">제안을 불러오지 못했습니다. (HTTP {res.status})</p>;
  }
  const p: AdminProposal = await res.json();
  const canDecide = p.status === "PENDING" && !p.stale;

  return (
    <div className="max-w-4xl space-y-4">
      <Link href="/agent/proposals" className="text-sm text-blue-600 hover:underline">
        ← 제안 목록
      </Link>
      <h1 className="text-xl font-semibold text-neutral-900">
        {p.characterName} 설정 변경 제안
      </h1>
      <p className="text-sm text-neutral-600">
        상태: <strong>{STATUS_LABEL[p.status]}</strong>
        {p.stale && p.status === "PENDING" && (
          <span className="ml-2 text-amber-700">현재 설정이 제안 이후 변경되었습니다. 새 제안이 필요합니다.</span>
        )}
      </p>

      <section className="rounded-lg border border-neutral-200 bg-white p-4">
        <h2 className="text-sm font-medium text-neutral-700">AI가 제시한 이유</h2>
        {/* 모델이 만든 텍스트이므로 항상 일반 텍스트로만 렌더링한다. */}
        <p className="mt-1 whitespace-pre-wrap text-sm text-neutral-900">{p.reason}</p>
      </section>

      {p.changedFields.map((field) => {
        const cleared = field === "ttsVoiceId" && p.proposed.ttsVoiceId === null;
        return (
          <section key={field} className="space-y-2 rounded-lg border border-neutral-200 bg-white p-4">
            <h2 className="text-sm font-medium text-neutral-700">{FIELD_LABEL[field]}</h2>
            <div className="grid gap-3 md:grid-cols-2">
              <div>
                <p className="mb-1 text-xs text-neutral-500">현재</p>
                <pre className="whitespace-pre-wrap rounded-md bg-red-50 p-2 text-sm">{show(p.current[field], false)}</pre>
              </div>
              <div>
                <p className="mb-1 text-xs text-neutral-500">제안</p>
                <pre className="whitespace-pre-wrap rounded-md bg-green-50 p-2 text-sm">
                  {show(p.proposed[field], cleared)}
                </pre>
              </div>
            </div>
          </section>
        );
      })}

      <ProposalDecision id={p.id} disabled={!canDecide} />
    </div>
  );
}
