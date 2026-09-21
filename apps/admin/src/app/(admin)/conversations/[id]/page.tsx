import Link from "next/link";
import { adminFetch } from "@/lib/backend";
import type { Message, PagedResponse } from "@/lib/types";

export default async function ConversationDetailPage({
  params,
  searchParams,
}: {
  params: Promise<{ id: string }>;
  searchParams: Promise<{ page?: string }>;
}) {
  const { id } = await params;
  const { page } = await searchParams;

  const res = await adminFetch(`/api/admin/conversations/${id}/messages?page=${page ?? "0"}`);
  const paged: PagedResponse<Message> = await res.json();

  return (
    <div className="space-y-4">
      <Link href="/conversations" className="text-sm text-blue-600 hover:underline">
        ← 대화 목록
      </Link>
      <h1 className="text-xl font-semibold text-neutral-900">대화 #{id}</h1>

      <div className="space-y-3 rounded-lg border border-neutral-200 bg-white p-4">
        {paged.content.map((message) => (
          <div key={message.id} className={`flex ${message.role === "ASSISTANT" ? "justify-start" : "justify-end"}`}>
            <div
              className={`max-w-lg rounded-lg px-3 py-2 text-sm ${
                message.role === "ASSISTANT" ? "bg-neutral-100 text-neutral-900" : "bg-blue-600 text-white"
              }`}
            >
              <p className="whitespace-pre-wrap">{message.content}</p>
              <p className="mt-1 text-[11px] opacity-60">{new Date(message.createdAt).toLocaleString("ko-KR")}</p>
            </div>
          </div>
        ))}
        {paged.content.length === 0 && <p className="text-center text-neutral-400">메시지가 없어요.</p>}
      </div>

      {paged.page.totalPages > 1 && (
        <div className="flex items-center justify-center gap-2 text-sm">
          {Array.from({ length: paged.page.totalPages }, (_, i) => i).map((p) => (
            <Link
              key={p}
              href={`/conversations/${id}?page=${p}`}
              className={`rounded-md px-2 py-1 ${
                p === paged.page.number ? "bg-neutral-900 text-white" : "text-neutral-600 hover:bg-neutral-200"
              }`}
            >
              {p + 1}
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
