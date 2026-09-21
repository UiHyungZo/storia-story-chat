"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { adminFetch } from "@/lib/backend";

export async function updateCharacterAction(id: string, formData: FormData) {
  const concept = formData.get("concept") as string;
  const systemPrompt = formData.get("systemPrompt") as string;
  const ttsVoiceId = (formData.get("ttsVoiceId") as string) || null;

  await adminFetch(`/api/admin/characters/${id}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ concept, systemPrompt, ttsVoiceId }),
  });

  revalidatePath("/characters");
  redirect("/characters");
}
