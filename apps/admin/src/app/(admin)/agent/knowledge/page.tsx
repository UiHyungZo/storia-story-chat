"use client";

import Link from "next/link";
import { useActionState } from "react";
import { reindexKnowledgeAction, searchKnowledgeAction } from "./actions";

export default function KnowledgePage() {
  const [search, searchAction, searching] = useActionState(searchKnowledgeAction, undefined);
  const [reindex, reindexAction, reindexing] = useActionState(reindexKnowledgeAction, undefined);
  const info = reindex?.index ?? search?.result?.index ?? null;

  return (
    <div className="max-w-4xl space-y-4">
      <Link href="/agent" className="text-sm text-blue-600 hover:underline">
        ← AI 조회
      </Link>
      <h1 className="text-xl font-semibold text-neutral-900">문서 검색 (RAG)</h1>
      <p className="text-sm text-neutral-500">
        허용된 프로젝트 문서만 벡터 검색합니다. 이 화면은 LLM을 거치지 않는 직접 검색이라 Gemini 호출 비용이 없습니다
        (임베딩 제공자가 gemini이면 질의 임베딩만 외부 호출). 대화 로그와 캐릭터 설정은 검색 대상이 아닙니다.
      </p>

      <form action={searchAction} className="space-y-3 rounded-lg border border-neutral-200 bg-white p-4">
        <input
          name="query"
          maxLength={300}
          required
          defaultValue={search?.query}
          placeholder="예: 음성 통화는 어떤 구조로 동작해?"
          className="w-full rounded-md border border-neutral-300 px-3 py-2 text-sm"
        />
        <div className="flex items-center gap-3 text-sm">
          <label htmlFor="topK">반환 개수</label>
          <select id="topK" name="topK" defaultValue="3" className="rounded-md border border-neutral-300 px-2 py-1">
            {[1, 2, 3, 4, 5].map((n) => (
              <option key={n}>{n}</option>
            ))}
          </select>
          <button
            type="submit"
            disabled={searching}
            className="rounded-md bg-neutral-900 px-3 py-2 text-sm font-medium text-white disabled:opacity-50"
          >
            {searching ? "검색 중…" : "검색"}
          </button>
        </div>
      </form>
      {search?.error && <p className="text-sm text-red-600">{search.error}</p>}

      {search?.result && (
        <section className="space-y-3">
          {search.result.status !== "OK" && (
            <p className="rounded-md bg-amber-50 p-3 text-sm text-amber-800">
              {search.result.status === "NO_EVIDENCE" ? "근거 문서를 찾지 못했습니다." : "인덱싱된 문서가 없습니다."}{" "}
              {search.result.message}
            </p>
          )}
          {search.result.results.map((hit) => (
            <article key={hit.chunkId} className="space-y-1 rounded-lg border border-neutral-200 bg-white p-4">
              <p className="text-sm">
                <span className="font-mono text-blue-700">{hit.source}</span> · {hit.heading || hit.title} ·{" "}
                <span className="text-neutral-500">점수 {hit.score.toFixed(3)}</span>
              </p>
              {/* 문서 발췌는 일반 텍스트로만 렌더링한다. */}
              <p className="whitespace-pre-wrap text-sm text-neutral-800">{hit.text}</p>
            </article>
          ))}
          <p className="text-xs text-neutral-400">{search.result.tookMs}ms</p>
        </section>
      )}

      <section className="space-y-2 rounded-lg border border-neutral-200 bg-white p-4 text-sm">
        <h2 className="font-medium text-neutral-700">인덱스</h2>
        {info ? (
          <p>
            제공자 <span className="font-mono">{info.provider}</span> ({info.semantic ? "의미 임베딩" : "어휘 기반 벡터 — 의미 임베딩 아님"}) ·
            문서 {info.documents}개 · 조각 {info.chunks}개 · 구축 {new Date(info.builtAt).toLocaleString("ko-KR")}
            {info.missingSources.length > 0 && <span className="text-amber-700"> · 누락 {info.missingSources.join(", ")}</span>}
          </p>
        ) : (
          <p className="text-neutral-400">검색하거나 재인덱싱하면 표시됩니다.</p>
        )}
        <form action={reindexAction}>
          {reindex?.error && <p className="mb-2 text-red-600">{reindex.error}</p>}
          <button
            type="submit"
            disabled={reindexing}
            className="rounded-md border border-neutral-300 px-3 py-2 text-neutral-700 disabled:opacity-50"
          >
            {reindexing ? "재인덱싱 중…" : "문서 변경 반영 (재인덱싱)"}
          </button>
        </form>
      </section>
    </div>
  );
}
