import AsyncStorage from "@react-native-async-storage/async-storage";

/**
 * Best-effort local mirror of server data (character list, per-character message
 * history) so screens can render instantly from the last known state while a
 * fresh fetch is in flight, and still show something if the fetch fails.
 */
const CACHE_PREFIX = "storia:cache:";

export async function readCache<T>(key: string): Promise<T | null> {
  try {
    const raw = await AsyncStorage.getItem(`${CACHE_PREFIX}${key}`);
    return raw ? (JSON.parse(raw) as T) : null;
  } catch {
    return null;
  }
}

export async function writeCache<T>(key: string, value: T): Promise<void> {
  try {
    await AsyncStorage.setItem(`${CACHE_PREFIX}${key}`, JSON.stringify(value));
  } catch {
    // Cache is a convenience layer, not the source of truth — ignore storage failures.
  }
}

export const CHARACTERS_CACHE_KEY = "characters";

export function messagesCacheKey(characterId: number): string {
  return `messages:${characterId}`;
}
