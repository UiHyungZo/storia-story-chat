import Link from "next/link";
import { adminFetch } from "@/lib/backend";
import type { AdminCharacter } from "@/lib/types";

export default async function CharactersPage() {
  const res = await adminFetch("/api/admin/characters");
  const characters: AdminCharacter[] = await res.json();

  return (
    <div className="space-y-4">
      <h1 className="text-xl font-semibold text-neutral-900">캐릭터</h1>
      <table className="w-full overflow-hidden rounded-lg border border-neutral-200 bg-white text-sm">
        <thead className="bg-neutral-100 text-left text-neutral-600">
          <tr>
            <th className="px-4 py-2">이름</th>
            <th className="px-4 py-2">컨셉</th>
            <th className="px-4 py-2">TTS 보이스</th>
            <th className="px-4 py-2"></th>
          </tr>
        </thead>
        <tbody>
          {characters.map((character) => (
            <tr key={character.id} className="border-t border-neutral-200">
              <td className="px-4 py-2 font-medium">{character.name}</td>
              <td className="max-w-xs truncate px-4 py-2 text-neutral-600">{character.concept}</td>
              <td className="px-4 py-2 text-neutral-500">{character.ttsVoiceId ?? "—"}</td>
              <td className="px-4 py-2 text-right">
                <Link href={`/characters/${character.id}`} className="text-blue-600 hover:underline">
                  수정
                </Link>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
