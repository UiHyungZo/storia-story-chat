"use client";

import Link from "next/link";
import { useActionState } from "react";
import { askAgentAction } from "./actions";

function proposalIdOf(result: string): string | null {
  try {
    const parsed = JSON.parse(result) as { proposalId?: unknown };
    return typeof parsed.proposalId === "string" ? parsed.proposalId : null;
  } catch {
    return null;
  }
}

const STOP_REASON_LABEL: Record<string, string> = {
  max_steps: "도구 호출 단계 제한에 도달했습니다.",
  timeout: "시간 제한에 도달했습니다.",
  no_response: "모델이 응답을 생성하지 못했습니다.",
};

export default function AgentPage() {
  const [state, formAction, pending] = useActionState(askAgentAction, undefined);

  return (
    <div className="max-w-3xl space-y-4">
      <h1 className="text-xl font-semibold text-neutral-900">AI 조회 도우미</h1>
      <p className="text-sm text-neutral-500">
        캐릭터 설정과 대화 로그를 읽기 전용으로 조회합니다. 데이터는 변경되지 않으며, 조회된 내용은 외부 AI(Gemini)로
        전송됩니다. 기기 ID는 마스킹되고 메시지는 일부만 전달됩니다. AI는 설정 변경을 &apos;제안&apos;만 할 수 있으며,{" "}
        <Link href="/agent/proposals" className="text-blue-600 hover:underline">
          제안 목록
        </Link>
        에서 관리자가 승인해야 반영됩니다.{" "}
        <Link href="/agent/observability" className="text-blue-600 hover:underline">
          실행 관측
        </Link>
        에서 지연·오류를 볼 수 있고,{" "}
        <Link href="/agent/knowledge" className="text-blue-600 hover:underline">
          문서 검색
        </Link>
        에서 프로젝트 문서를 직접 검색할 수 있습니다.
      </p>

      <form action={formAction} className="space-y-3 rounded-lg border border-neutral-200 bg-white p-4">
        <label htmlFor="question" className="text-sm text-neutral-600">
          질문
        </label>
        <textarea
          id="question"
          name="question"
          rows={3}
          maxLength={1000}
          required
          defaultValue={state?.question}
          placeholder="예: 1번 캐릭터의 시스템 프롬프트를 요약해줘"
          className="w-full rounded-md border border-neutral-300 px-3 py-2 text-sm"
        />
        <button
          type="submit"
          disabled={pending}
          className="rounded-md bg-neutral-900 px-3 py-2 text-sm font-medium text-white disabled:opacity-50"
        >
          {pending ? "조회 중…" : "질문하기"}
        </button>
      </form>

      {state?.error && <p className="text-sm text-red-600">{state.error}</p>}

      {state?.response && (
        <div className="space-y-4">
          {state.response.evidenceNotice && (
            <p className="rounded-md bg-amber-50 p-3 text-sm text-amber-800">{state.response.evidenceNotice}</p>
          )}
          <section className="space-y-2 rounded-lg border border-neutral-200 bg-white p-4">
            <h2 className="text-sm font-medium text-neutral-700">답변</h2>
            <p className="whitespace-pre-wrap text-sm text-neutral-900">{state.response.answer}</p>
            {state.response.knowledgeSources.length > 0 && (
              <div className="text-xs text-neutral-600">
                근거 문서:{" "}
                {state.response.knowledgeSources.map((s) => (
                  <span key={s} className="mr-2 rounded bg-neutral-100 px-1.5 py-0.5 font-mono">
                    {s}
                  </span>
                ))}
              </div>
            )}
            {state.response.stopReason !== "completed" && (
              <p className="text-xs text-amber-700">
                {STOP_REASON_LABEL[state.response.stopReason] ?? state.response.stopReason}
              </p>
            )}
          </section>

          <section className="space-y-2 rounded-lg border border-neutral-200 bg-white p-4">
            <h2 className="text-sm font-medium text-neutral-700">도구 호출 내역 ({state.response.toolCalls.length})</h2>
            {state.response.toolCalls.length === 0 && <p className="text-sm text-neutral-400">호출된 도구가 없습니다.</p>}
            {state.response.toolCalls.map((call, index) => (
              <details key={index} className="rounded-md border border-neutral-200 p-2 text-sm">
                <summary className="cursor-pointer">
                  <span className="font-mono">{call.name}</span>{" "}
                  <span className={call.ok ? "text-green-700" : "text-red-600"}>{call.ok ? "성공" : "실패"}</span>
                </summary>
                <p className="mt-2 text-xs text-neutral-500">인자</p>
                <pre className="overflow-x-auto whitespace-pre-wrap text-xs">{call.arguments}</pre>
                <p className="mt-2 text-xs text-neutral-500">결과</p>
                <pre className="overflow-x-auto whitespace-pre-wrap text-xs">{call.result}</pre>
                {call.ok && proposalIdOf(call.result) && (
                  <Link
                    href={`/agent/proposals/${encodeURIComponent(proposalIdOf(call.result)!)}`}
                    className="mt-2 inline-block text-sm text-blue-600 hover:underline"
                  >
                    제안 검토하기 →
                  </Link>
                )}
              </details>
            ))}
          </section>
        </div>
      )}
    </div>
  );
}
