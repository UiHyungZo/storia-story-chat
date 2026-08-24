import { create } from "zustand";
import { fetchCharacters } from "../api/characters";
import { CHARACTERS_CACHE_KEY, readCache, writeCache } from "../storage/cache";
import { Character } from "../types";

type CharacterStore = {
  characters: Character[];
  isLoading: boolean;
  error: string | null;
  loadCharacters: () => Promise<void>;
  getCharacterById: (id: number) => Character | undefined;
};

export const useCharacterStore = create<CharacterStore>((set, get) => ({
  characters: [],
  isLoading: false,
  error: null,
  loadCharacters: async () => {
    if (get().characters.length === 0) {
      const cached = await readCache<Character[]>(CHARACTERS_CACHE_KEY);
      // Guard against a fetch that already landed while we were reading the cache.
      if (cached && get().characters.length === 0) {
        set({ characters: cached });
      }
    }

    set({ isLoading: true, error: null });
    try {
      const characters = await fetchCharacters();
      set({ characters, isLoading: false });
      writeCache(CHARACTERS_CACHE_KEY, characters);
    } catch (error) {
      set({ error: error instanceof Error ? error.message : String(error), isLoading: false });
    }
  },
  getCharacterById: (id) => get().characters.find((c) => c.id === id),
}));
