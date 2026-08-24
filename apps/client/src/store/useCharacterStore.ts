import { create } from "zustand";
import { fetchCharacters } from "../api/characters";
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
    set({ isLoading: true, error: null });
    try {
      const characters = await fetchCharacters();
      set({ characters, isLoading: false });
    } catch (error) {
      set({ error: error instanceof Error ? error.message : String(error), isLoading: false });
    }
  },
  getCharacterById: (id) => get().characters.find((c) => c.id === id),
}));
