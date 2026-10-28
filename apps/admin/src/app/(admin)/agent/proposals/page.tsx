import Link from "next/link";
import { adminFetch } from "@/lib/backend";
import type { AdminProposal } from "@/lib/types";

const STATUS_LABEL: Record<AdminProposal["status"], string> = {
  PENDING: "승인 대기",
  APPROVED: "승인됨",
  REJECTED: "거절됨",
  STALE: "오래됨",
};

export default async function ProposalsPage() {
  const res = await adminFetch("/api/admin/ai/proposals");
  const proposals: AdminProposal[] = res.ok ? await res.json() : [];

  return (
    <div className="max-w-3xl space-y-4">
      <Link href="/agent" className="text-sm text-blue-600 hover:underline">
        ← AI 조회
      </Link>
      <h1 className="text-xl font-semibold text-neutral-900">설정 변경 제안</h1>
      <p className="text-sm text-neutral-500">
        제안은 서버 메모리에만 보관되며 24시간 후 또는 서버 재시작 시 사라집니다. 승인하기 전에는 아무 설정도 바뀌지 않습니다.
      </p>
      {!res.ok && <p className="text-sm text-red-600">제안 목록을 불러오지 못했습니다. (HTTP {res.status})</p>}
      <ul className="divide-y divide-neutral-200 rounded-lg border border-neutral-200 bg-white">
        {proposals.map((p) => (
          <li key={p.id} className="flex items-center justify-between px-4 py-3 text-sm">
            <Link href={`/agent/proposals/${encodeURIComponent(p.id)}`} className="text-blue-600 hover:underline">
              {p.characterName} — {p.changedFields.join(", ")}
            </Link>
            <span className="text-neutral-500">
              {STATUS_LABEL[p.status]}
              {p.stale && p.status === "PENDING" ? " (설정이 변경됨)" : ""} ·{" "}
              {new Date(p.createdAt).toLocaleString("ko-KR")}
            </span>
          </li>
        ))}
        {res.ok && proposals.length === 0 && <li className="px-4 py-6 text-center text-neutral-400">제안이 없습니다.</li>}
      </ul>
    </div>
  );
}
