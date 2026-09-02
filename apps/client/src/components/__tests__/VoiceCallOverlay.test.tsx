import { render, screen, fireEvent } from "@testing-library/react-native";
import { VoiceCallOverlay } from "../VoiceCallOverlay";

// The real store pulls in @livekit/react-native, livekit-client and expo-audio
// at module load, none of which resolve under jest — mock it and drive the
// component with a plain state object + jest.fn() actions.
jest.mock("../../store/useVoiceCallStore", () => ({ useVoiceCallStore: jest.fn() }));
import { useVoiceCallStore } from "../../store/useVoiceCallStore";

const mockUse = useVoiceCallStore as unknown as jest.Mock;

type OverlayState = Record<string, unknown>;

function primeStore(overrides: OverlayState = {}) {
  const state: OverlayState = {
    isCallActive: true,
    phase: "idle",
    mode: "turn",
    agentSpeaking: false,
    errorMessage: null,
    startListening: jest.fn(),
    stopListening: jest.fn(),
    endCall: jest.fn(),
    ...overrides,
  };
  mockUse.mockImplementation((selector: (s: OverlayState) => unknown) => selector(state));
  return state;
}

afterEach(() => mockUse.mockReset());

describe("VoiceCallOverlay — turn mode", () => {
  it("shows the idle prompt and starts listening when the mic is tapped", () => {
    const state = primeStore({ phase: "idle" });
    render(<VoiceCallOverlay characterId={1} characterName="렌" />);

    expect(screen.getByText("마이크를 눌러 말해보세요")).toBeOnTheScreen();

    fireEvent.press(screen.getByText("🎤"));
    expect(state.startListening).toHaveBeenCalledTimes(1);
  });

  it("swaps the mic button to stop and ends the turn when tapped again", () => {
    const state = primeStore({ phase: "listening" });
    render(<VoiceCallOverlay characterId={1} characterName="렌" />);

    expect(screen.queryByText("🎤")).toBeNull();
    fireEvent.press(screen.getByText("■"));
    expect(state.stopListening).toHaveBeenCalledTimes(1);
  });

  it("disables the mic button and shows a spinner while a reply is in flight", () => {
    primeStore({ phase: "thinking" });
    render(<VoiceCallOverlay characterId={1} characterName="렌" />);

    expect(screen.getByText("생각하는 중…")).toBeOnTheScreen();
    expect(screen.getByText("🎤")).toBeDisabled();
  });
});

describe("VoiceCallOverlay — agent mode", () => {
  it("shows the live-call badge and the speaking status", () => {
    primeStore({ mode: "agent", phase: "listening", agentSpeaking: true });
    render(<VoiceCallOverlay characterId={2} characterName="루나" />);

    expect(screen.getByText("실시간 통화")).toBeOnTheScreen();
    expect(screen.getByText("루나 말하는 중…")).toBeOnTheScreen();
  });

  it("hides the mic button once the mic is live (full duplex)", () => {
    primeStore({ mode: "agent", phase: "listening", agentSpeaking: false });
    render(<VoiceCallOverlay characterId={2} characterName="루나" />);

    expect(screen.queryByText("🎤")).toBeNull();
    expect(screen.queryByText("■")).toBeNull();
    expect(screen.getByText("듣고 있어요 — 자유롭게 말하세요")).toBeOnTheScreen();
  });
});

describe("VoiceCallOverlay — shared", () => {
  it("surfaces an error message over the phase label", () => {
    primeStore({ phase: "listening", errorMessage: "마이크 권한이 없어요" });
    render(<VoiceCallOverlay characterId={1} characterName="렌" />);

    expect(screen.getByText("마이크 권한이 없어요")).toBeOnTheScreen();
  });

  it("ends the call when 통화 종료 is pressed", () => {
    const state = primeStore();
    render(<VoiceCallOverlay characterId={1} characterName="렌" />);

    fireEvent.press(screen.getByText("통화 종료"));
    expect(state.endCall).toHaveBeenCalledTimes(1);
  });
});
