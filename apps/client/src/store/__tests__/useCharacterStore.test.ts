import { useCharacterStore } from "../useCharacterStore";
import { fetchCharacters } from "../../api/characters";
import { readCache, writeCache } from "../../storage/cache";
import { Character } from "../../types";

// Explicit factories (not bare jest.mock(path)) so Jest never has to load the
// real modules, which transitively pull in AsyncStorage's native module.
jest.mock("../../api/characters", () => ({ fetchCharacters: jest.fn() }));
jest.mock("../../storage/cache", () => ({
  readCache: jest.fn(),
  writeCache: jest.fn(),
  CHARACTERS_CACHE_KEY: "characters",
}));

const mockedFetchCharacters = fetchCharacters as jest.MockedFunction<typeof fetchCharacters>;
const mockedReadCache = readCache as jest.MockedFunction<typeof readCache>;
const mockedWriteCache = writeCache as jest.MockedFunction<typeof writeCache>;

const CHARACTERS: Character[] = [
  { id: 1, name: "루나", concept: "따뜻한 상담사", ttsVoiceId: null },
];

describe("useCharacterStore", () => {
  const initialState = useCharacterStore.getState();

  beforeEach(() => {
    useCharacterStore.setState(initialState, true);
    jest.clearAllMocks();
    mockedReadCache.mockResolvedValue(null);
  });

  it("hydrates from cache immediately, then overwrites with the fetch result", async () => {
    mockedReadCache.mockResolvedValue([{ id: 99, name: "cached", concept: "", ttsVoiceId: null }]);
    mockedFetchCharacters.mockResolvedValue(CHARACTERS);

    const promise = useCharacterStore.getState().loadCharacters();
    await promise;

    expect(useCharacterStore.getState().characters).toEqual(CHARACTERS);
    expect(mockedWriteCache).toHaveBeenCalledWith("characters", CHARACTERS);
  });

  it("sets an error and stops loading when the fetch fails", async () => {
    mockedFetchCharacters.mockRejectedValue(new Error("network down"));

    await useCharacterStore.getState().loadCharacters();

    expect(useCharacterStore.getState().error).toBe("network down");
    expect(useCharacterStore.getState().isLoading).toBe(false);
  });

  it("does not re-fetch cache once characters are already loaded", async () => {
    useCharacterStore.setState({ characters: CHARACTERS });
    mockedFetchCharacters.mockResolvedValue(CHARACTERS);

    await useCharacterStore.getState().loadCharacters();

    expect(mockedReadCache).not.toHaveBeenCalled();
  });

  it("getCharacterById finds the matching character", () => {
    useCharacterStore.setState({ characters: CHARACTERS });

    expect(useCharacterStore.getState().getCharacterById(1)?.name).toBe("루나");
    expect(useCharacterStore.getState().getCharacterById(404)).toBeUndefined();
  });
});
