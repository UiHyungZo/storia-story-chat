import { adminFetch } from "@/lib/backend";
import type { AdminCharacter } from "@/lib/types";
import { updateCharacterAction } from "./actions";

export default async function CharacterEditPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const res = await adminFetch(`/api/admin/characters/${id}`);
  const character: AdminCharacter = await res.json();
  const action = updateCharacterAction.bind(null, id);

  return (
    <div className="max-w-2xl space-y-4">
      <h1 className="text-xl font-semibold text-neutral-900">{character.name}</h1>
      <form action={action} className="space-y-4 rounded-lg border border-neutral-200 bg-white p-6">
        <div className="space-y-1">
          <label htmlFor="concept" className="text-sm text-neutral-600">
            컨셉
          </label>
          <input
            id="concept"
            name="concept"
            defaultValue={character.concept}
            required
            className="w-full rounded-md border border-neutral-300 px-3 py-2 text-sm"
          />
        </div>
        <div className="space-y-1">
          <label htmlFor="systemPrompt" className="text-sm text-neutral-600">
            시스템 프롬프트
          </label>
          <textarea
            id="systemPrompt"
            name="systemPrompt"
            defaultValue={character.systemPrompt}
            required
            rows={10}
            className="w-full rounded-md border border-neutral-300 px-3 py-2 font-mono text-sm"
          />
        </div>
        <div className="space-y-1">
          <label htmlFor="ttsVoiceId" className="text-sm text-neutral-600">
            TTS 보이스 ID
          </label>
          <input
            id="ttsVoiceId"
            name="ttsVoiceId"
            defaultValue={character.ttsVoiceId ?? ""}
            className="w-full rounded-md border border-neutral-300 px-3 py-2 text-sm"
          />
        </div>
        <button type="submit" className="rounded-md bg-neutral-900 px-4 py-2 text-sm font-medium text-white">
          저장
        </button>
      </form>
    </div>
  );
}
