import Link from "next/link";
import { adminFetch } from "@/lib/backend";
import type { AdminCharacter, AdminConversationSummary, PageMeta, PagedResponse } from "@/lib/types";

type SearchParams = {
  characterId?: string;
  deviceId?: string;
  from?: string;
  to?: string;
  page?: string;
};

export default async function ConversationsPage({
  searchParams,
}: {
  searchParams: Promise<SearchParams>;
}) {
  const sp = await searchParams;

  const charactersRes = await adminFetch("/api/admin/characters");
  const characters: AdminCharacter[] = await charactersRes.json();

  const query = new URLSearchParams();
  if (sp.characterId) query.set("characterId", sp.characterId);
  if (sp.deviceId) query.set("deviceId", sp.deviceId);
  if (sp.from) query.set("from", sp.from);
  if (sp.to) query.set("to", sp.to);
  query.set("page", sp.page ?? "0");

  const res = await adminFetch(`/api/admin/conversations?${query.toString()}`);
  const paged: PagedResponse<AdminConversationSummary> = await res.json();

  return (
    <div className="space-y-4">
      <h1 className="text-xl font-semibold text-neutral-900">대화 로그</h1>

      <form
        method="get"
        className="flex flex-wrap items-end gap-3 rounded-lg border border-neutral-200 bg-white p-4"
      >
        <div className="space-y-1">
          <label className="block text-xs text-neutral-500">캐릭터</label>
          <select
            name="characterId"
            defaultValue={sp.characterId ?? ""}
            className="rounded-md border border-neutral-300 px-2 py-1.5 text-sm"
          >
            <option value="">전체</option>
            {characters.map((character) => (
              <option key={character.id} value={character.id}>
                {character.name}
              </option>
            ))}
          </select>
        </div>
        <div className="space-y-1">
          <label className="block text-xs text-neutral-500">디바이스 ID</label>
          <input
            name="deviceId"
            defaultValue={sp.deviceId ?? ""}
            className="rounded-md border border-neutral-300 px-2 py-1.5 text-sm"
          />
        </div>
        <div className="space-y-1">
          <label className="block text-xs text-neutral-500">시작일</label>
          <input
            type="date"
            name="from"
            defaultValue={sp.from ?? ""}
            className="rounded-md border border-neutral-300 px-2 py-1.5 text-sm"
          />
        </div>
        <div className="space-y-1">
          <label className="block text-xs text-neutral-500">종료일</label>
          <input
            type="date"
            name="to"
            defaultValue={sp.to ?? ""}
            className="rounded-md border border-neutral-300 px-2 py-1.5 text-sm"
          />
        </div>
        <button type="submit" className="rounded-md bg-neutral-900 px-3 py-1.5 text-sm font-medium text-white">
          필터 적용
        </button>
      </form>

      <table className="w-full overflow-hidden rounded-lg border border-neutral-200 bg-white text-sm">
        <thead className="bg-neutral-100 text-left text-neutral-600">
          <tr>
            <th className="px-4 py-2">캐릭터</th>
            <th className="px-4 py-2">디바이스 ID</th>
            <th className="px-4 py-2">생성일</th>
            <th className="px-4 py-2"></th>
          </tr>
        </thead>
        <tbody>
          {paged.content.map((conversation) => (
            <tr key={conversation.id} className="border-t border-neutral-200">
              <td className="px-4 py-2 font-medium">{conversation.characterName}</td>
              <td className="px-4 py-2 font-mono text-xs text-neutral-600">{conversation.deviceId}</td>
              <td className="px-4 py-2 text-neutral-500">
                {new Date(conversation.createdAt).toLocaleString("ko-KR")}
              </td>
              <td className="px-4 py-2 text-right">
                <Link href={`/conversations/${conversation.id}`} className="text-blue-600 hover:underline">
                  보기
                </Link>
              </td>
            </tr>
          ))}
          {paged.content.length === 0 && (
            <tr>
              <td colSpan={4} className="px-4 py-6 text-center text-neutral-400">
                조건에 맞는 대화가 없어요.
              </td>
            </tr>
          )}
        </tbody>
      </table>

      <Pagination page={paged.page} searchParams={sp} />
    </div>
  );
}

function Pagination({ page, searchParams }: { page: PageMeta; searchParams: SearchParams }) {
  if (page.totalPages <= 1) return null;

  const buildHref = (targetPage: number) => {
    const q = new URLSearchParams();
    if (searchParams.characterId) q.set("characterId", searchParams.characterId);
    if (searchParams.deviceId) q.set("deviceId", searchParams.deviceId);
    if (searchParams.from) q.set("from", searchParams.from);
    if (searchParams.to) q.set("to", searchParams.to);
    q.set("page", String(targetPage));
    return `/conversations?${q.toString()}`;
  };

  return (
    <div className="flex items-center justify-center gap-2 text-sm">
      {Array.from({ length: page.totalPages }, (_, i) => i).map((p) => (
        <Link
          key={p}
          href={buildHref(p)}
          className={`rounded-md px-2 py-1 ${
            p === page.number ? "bg-neutral-900 text-white" : "text-neutral-600 hover:bg-neutral-200"
          }`}
        >
          {p + 1}
        </Link>
      ))}
    </div>
  );
}
