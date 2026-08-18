import { create } from "zustand";
import { DUMMY_CHARACTERS } from "../constants/dummyCharacters";
import { Character } from "../types";

type CharacterStore = {
  characters: Character[];
  getCharacterById: (id: string) => Character | undefined;
};

export const useCharacterStore = create<CharacterStore>((set, get) => ({
  characters: DUMMY_CHARACTERS,
  getCharacterById: (id) => get().characters.find((c) => c.id === id),
}));
