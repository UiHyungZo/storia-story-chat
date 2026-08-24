import { API_BASE_URL } from "./config";

const AVAILABILITY_CHECK_TIMEOUT_MS = 3000;

export function getMessageAudioUrl(messageId: number): string {
  return `${API_BASE_URL}/api/messages/${messageId}/audio`;
}

/**
 * `GET .../audio` 404s when TTS isn't configured on the backend or synthesis
 * failed (see TtsService) — check before handing the URL to the audio player,
 * since a 404 source would otherwise just hang playback with no error/finish
 * event to resolve on.
 */
export async function isMessageAudioAvailable(messageId: number): Promise<boolean> {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), AVAILABILITY_CHECK_TIMEOUT_MS);
  try {
    const response = await fetch(getMessageAudioUrl(messageId), {
      method: "HEAD",
      signal: controller.signal,
    });
    return response.ok;
  } catch {
    return false;
  } finally {
    clearTimeout(timeout);
  }
}
