import { render, screen, fireEvent } from "@testing-library/react-native";
import { CharacterListScreen } from "../CharacterListScreen";
import { Character } from "../../types";

jest.mock("../../store/useCharacterStore", () => ({ useCharacterStore: jest.fn() }));
import { useCharacterStore } from "../../store/useCharacterStore";

const mockUse = useCharacterStore as unknown as jest.Mock;

const CHARACTERS: Character[] = [
  { id: 1, name: "루나", concept: "따뜻한 상담사", ttsVoiceId: null },
  { id: 2, name: "렌", concept: "시간을 파는 서점 주인", ttsVoiceId: null },
];

type ListState = Record<string, unknown>;

function primeStore(overrides: ListState = {}) {
  const state: ListState = {
    characters: [],
    isLoading: false,
    error: null,
    loadCharacters: jest.fn(),
    ...overrides,
  };
  mockUse.mockImplementation((selector: (s: ListState) => unknown) => selector(state));
  return state;
}

function renderScreen() {
  const navigation = { navigate: jest.fn() };
  render(<CharacterListScreen navigation={navigation as never} route={{ key: "k", name: "CharacterList" } as never} />);
  return navigation;
}

afterEach(() => mockUse.mockReset());

describe("CharacterListScreen", () => {
  it("loads characters on mount", () => {
    const state = primeStore();
    renderScreen();
    expect(state.loadCharacters).toHaveBeenCalledTimes(1);
  });

  it("renders a row per character", () => {
    primeStore({ characters: CHARACTERS });
    renderScreen();

    expect(screen.getByText("루나")).toBeOnTheScreen();
    expect(screen.getByText("렌")).toBeOnTheScreen();
    expect(screen.getByText("따뜻한 상담사")).toBeOnTheScreen();
  });

  it("navigates to the chat room with the character id when a row is tapped", () => {
    primeStore({ characters: CHARACTERS });
    const navigation = renderScreen();

    fireEvent.press(screen.getByText("렌"));

    expect(navigation.navigate).toHaveBeenCalledWith("ChatRoom", { characterId: 2 });
  });

  it("shows the error banner and retries the load when tapped", () => {
    const state = primeStore({ error: "불러오지 못했어요" });
    renderScreen();

    expect(screen.getByText("불러오지 못했어요")).toBeOnTheScreen();
    fireEvent.press(screen.getByText("다시 시도"));

    // once on mount + once on the retry tap
    expect(state.loadCharacters).toHaveBeenCalledTimes(2);
  });

  it("shows 재시도 중… on the banner while a retry is loading", () => {
    primeStore({ error: "불러오지 못했어요", isLoading: true });
    renderScreen();

    expect(screen.getByText("재시도 중…")).toBeOnTheScreen();
  });
});
