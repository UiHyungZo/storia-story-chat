import { apiGet } from "./client";
import { Character } from "../types";

type CharacterResponseDto = {
  id: number;
  name: string;
  concept: string;
  ttsVoiceId: string | null;
};

function fromDto(dto: CharacterResponseDto): Character {
  return {
    id: dto.id,
    name: dto.name,
    concept: dto.concept,
    ttsVoiceId: dto.ttsVoiceId,
  };
}

export async function fetchCharacters(): Promise<Character[]> {
  const dtos = await apiGet<CharacterResponseDto[]>("/api/characters");
  return dtos.map(fromDto);
}
