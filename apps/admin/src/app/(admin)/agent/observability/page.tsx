import Link from "next/link";
import { adminFetch } from "@/lib/backend";
import type { AgentObservability } from "@/lib/types";

const fmt = (n: number | null, unit = "") => (n === null ? "—" : `${Math.round(n)}${unit}`);

export default async function ObservabilityPage() {
  const res = await adminFetch("/api/admin/ai/observability");
  if (!res.ok) {
    return <p className="text-sm text-red-600">관측 데이터를 불러오지 못했습니다. (HTTP {res.status})</p>;
  }
  const { summary, recentRuns }: AgentObservability = await res.json();

  return (
    <div className="max-w-4xl space-y-4">
      <Link href="/agent" className="text-sm text-blue-600 hover:underline">
        ← AI 조회
      </Link>
      <h1 className="text-xl font-semibold text-neutral-900">AI 실행 관측</h1>
      <p className="text-sm text-neutral-500">
        최근 {summary.retentionHours}시간, 최대 {summary.maxRecords}건의 실행 메타데이터(지연, 단계, 도구, 오류 유형, 토큰)만
        서버 메모리에 보관합니다. 질문·답변·대화·설정 내용은 기록하지 않으며 서버 재시작 시 사라집니다. 토큰이 &quot;—&quot;이면
        모델이 사용량을 보고하지 않은 것입니다.
      </p>

      <section className="grid grid-cols-2 gap-3 rounded-lg border border-neutral-200 bg-white p-4 text-sm md:grid-cols-4">
        <div>실행 수<p className="text-lg font-semibold">{summary.runs}</p></div>
        <div>평균 소요<p className="text-lg font-semibold">{fmt(summary.avgDurationMs, "ms")}</p></div>
        <div>p95 소요<p className="text-lg font-semibold">{fmt(summary.p95DurationMs, "ms")}</p></div>
        <div>평균 모델 지연<p className="text-lg font-semibold">{fmt(summary.avgModelLatencyMs, "ms")}</p></div>
        <div>보고된 토큰 합<p className="text-lg font-semibold">{fmt(summary.totalTokens)}</p></div>
        <div className="col-span-2 md:col-span-3">
          종료 사유
          <p className="font-mono text-xs">
            {Object.entries(summary.byStopReason).map(([k, v]) => `${k}: ${v}`).join(" · ") || "—"}
          </p>
          오류 유형
          <p className="font-mono text-xs">
            {Object.entries(summary.byErrorType).map(([k, v]) => `${k}: ${v}`).join(" · ") || "없음"}
          </p>
        </div>
      </section>

      <section className="space-y-2 rounded-lg border border-neutral-200 bg-white p-4">
        <h2 className="text-sm font-medium text-neutral-700">도구별 통계</h2>
        <table className="w-full text-left text-sm">
          <thead className="text-xs text-neutral-500">
            <tr><th>도구</th><th>호출</th><th>오류</th><th>평균 소요</th></tr>
          </thead>
          <tbody>
            {summary.tools.map((t) => (
              <tr key={t.name}>
                <td className="font-mono">{t.name}</td><td>{t.calls}</td><td>{t.errors}</td><td>{fmt(t.avgDurationMs, "ms")}</td>
              </tr>
            ))}
            {summary.tools.length === 0 && <tr><td colSpan={4} className="text-neutral-400">기록 없음</td></tr>}
          </tbody>
        </table>
      </section>

      <section className="space-y-2 rounded-lg border border-neutral-200 bg-white p-4">
        <h2 className="text-sm font-medium text-neutral-700">최근 실행</h2>
        <table className="w-full text-left text-sm">
          <thead className="text-xs text-neutral-500">
            <tr><th>시각</th><th>종료</th><th>소요</th><th>모델 호출</th><th>도구</th><th>토큰</th></tr>
          </thead>
          <tbody>
            {recentRuns.map((r) => (
              <tr key={r.runId}>
                <td>{new Date(r.startedAt).toLocaleString("ko-KR")}</td>
                <td>{r.errorType ?? r.stopReason}</td>
                <td>{r.durationMs}ms</td>
                <td>{r.modelCalls}</td>
                <td>{r.tools.map((t) => `${t.name}${t.ok ? "" : `✗${t.errorCode ?? ""}`}`).join(", ") || "—"}</td>
                <td>{fmt(r.totalTokens)}</td>
              </tr>
            ))}
            {recentRuns.length === 0 && <tr><td colSpan={6} className="text-neutral-400">기록 없음</td></tr>}
          </tbody>
        </table>
      </section>
    </div>
  );
}
