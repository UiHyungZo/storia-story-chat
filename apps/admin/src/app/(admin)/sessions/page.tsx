import Link from "next/link";
import { adminFetch } from "@/lib/backend";
import type { AdminVoiceTurn } from "@/lib/types";

const STATUS_LABEL: Record<AdminVoiceTurn["status"], string> = {
  recording: "녹음 중",
  processing: "처리 중",
  done: "완료",
  error: "오류",
};

const STATUS_COLOR: Record<AdminVoiceTurn["status"], string> = {
  recording: "bg-amber-100 text-amber-700",
  processing: "bg-blue-100 text-blue-700",
  done: "bg-green-100 text-green-700",
  error: "bg-red-100 text-red-700",
};

export default async function SessionsPage() {
  const res = await adminFetch("/api/admin/sessions");
  const turns: AdminVoiceTurn[] = await res.json();

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold text-neutral-900">진행 중인 음성 세션</h1>
        <Link href="/sessions" className="text-sm text-blue-600 hover:underline">
          새로고침
        </Link>
      </div>
      <table className="w-full overflow-hidden rounded-lg border border-neutral-200 bg-white text-sm">
        <thead className="bg-neutral-100 text-left text-neutral-600">
          <tr>
            <th className="px-4 py-2">Turn ID</th>
            <th className="px-4 py-2">디바이스</th>
            <th className="px-4 py-2">캐릭터 ID</th>
            <th className="px-4 py-2">상태</th>
            <th className="px-4 py-2">시작 시각</th>
            <th className="px-4 py-2">에러</th>
          </tr>
        </thead>
        <tbody>
          {turns.map((turn) => (
            <tr key={turn.turnId} className="border-t border-neutral-200">
              <td className="px-4 py-2 font-mono text-xs">{turn.turnId}</td>
              <td className="px-4 py-2 font-mono text-xs text-neutral-600">{turn.deviceId}</td>
              <td className="px-4 py-2">{turn.characterId}</td>
              <td className="px-4 py-2">
                <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_COLOR[turn.status]}`}>
                  {STATUS_LABEL[turn.status]}
                </span>
              </td>
              <td className="px-4 py-2 text-neutral-500">{new Date(turn.createdAt).toLocaleString("ko-KR")}</td>
              <td className="px-4 py-2 text-red-600">{turn.errorMessage ?? "—"}</td>
            </tr>
          ))}
          {turns.length === 0 && (
            <tr>
              <td colSpan={6} className="px-4 py-6 text-center text-neutral-400">
                진행 중인 세션이 없어요.
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
