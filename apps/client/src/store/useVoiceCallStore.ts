import { AudioPlayer, createAudioPlayer } from "expo-audio";
import { ExpoSpeechRecognitionModule } from "expo-speech-recognition";
import { create } from "zustand";
import { getMessageAudioUrl, isMessageAudioAvailable } from "../api/tts";
import { useConversationStore } from "./useConversationStore";

const SPEECH_LOCALE = "ko-KR";

export type CallPhase = "idle" | "listening" | "thinking" | "speaking" | "error";

type VoiceCallStore = {
  isCallActive: boolean;
  characterId: number | null;
  phase: CallPhase;
  transcript: string;
  errorMessage: string | null;
  startCall: (characterId: number) => Promise<void>;
  endCall: () => void;
  startListening: () => void;
  stopListening: () => void;
};

let player: AudioPlayer | null = null;
let listenersAttached = false;

function stopAndReleasePlayer(): void {
  if (!player) return;
  player.pause();
  player.remove();
  player = null;
}

/**
 * Turn-based voice call (PRD 3.9 B안 — no WebRTC): on-device STT fills the
 * turn's text, which goes through the same REST send path text chat uses
 * (`sendMessageViaRest`), then the assistant reply is played back via TTS
 * audio fetched from the backend. Not full-duplex — the mic only reopens
 * when the user taps it again after playback finishes.
 */
export const useVoiceCallStore = create<VoiceCallStore>((set, get) => {
  function attachListenersOnce(): void {
    if (listenersAttached) return;
    listenersAttached = true;

    ExpoSpeechRecognitionModule.addListener("result", (event) => {
      const transcript = event.results[0]?.transcript ?? "";
      set({ transcript });
      if (event.isFinal && transcript.trim() && get().phase === "listening") {
        void submitTranscript();
      }
    });

    ExpoSpeechRecognitionModule.addListener("error", (event) => {
      if (event.error === "no-speech" || event.error === "aborted") {
        set((state) => (state.isCallActive ? { phase: "idle" } : state));
        return;
      }
      set({ phase: "error", errorMessage: event.message });
    });

    ExpoSpeechRecognitionModule.addListener("end", () => {
      // Recognition can end without a final result (silence timeout) — go back
      // to idle so the mic button is tappable again instead of stuck "listening".
      set((state) => (state.phase === "listening" ? { phase: "idle" } : state));
    });
  }

  async function submitTranscript(): Promise<void> {
    const { characterId, transcript } = get();
    const content = transcript.trim();
    if (!characterId || !content) {
      set({ phase: "idle" });
      return;
    }

    set({ phase: "thinking" });
    try {
      const assistantMessage = await useConversationStore
        .getState()
        .sendMessageViaRest(characterId, content);
      await playAssistantAudio(assistantMessage.id);
    } catch (error) {
      set({ phase: "error", errorMessage: error instanceof Error ? error.message : String(error) });
    }
  }

  async function playAssistantAudio(messageId: number): Promise<void> {
    const available = await isMessageAudioAvailable(messageId);
    if (!available) {
      // TTS not configured/failed on the backend — reply text is already in the
      // chat history, just skip straight back to idle for the next turn.
      set((state) => (state.isCallActive ? { phase: "idle" } : state));
      return;
    }

    set({ phase: "speaking" });
    stopAndReleasePlayer();
    player = createAudioPlayer(getMessageAudioUrl(messageId));

    await new Promise<void>((resolve) => {
      const subscription = player!.addListener("playbackStatusUpdate", (status) => {
        if (status.didJustFinish) {
          subscription.remove();
          resolve();
        }
      });
      player!.play();
    });

    stopAndReleasePlayer();
    set((state) => (state.isCallActive ? { phase: "idle" } : state));
  }

  return {
    isCallActive: false,
    characterId: null,
    phase: "idle",
    transcript: "",
    errorMessage: null,

    startCall: async (characterId) => {
      attachListenersOnce();
      const permission = await ExpoSpeechRecognitionModule.requestPermissionsAsync();
      if (!permission.granted) {
        set({
          isCallActive: true,
          characterId,
          phase: "error",
          errorMessage: "마이크/음성 인식 권한이 필요해요.",
        });
        return;
      }
      set({ isCallActive: true, characterId, phase: "idle", transcript: "", errorMessage: null });
    },

    endCall: () => {
      ExpoSpeechRecognitionModule.abort();
      stopAndReleasePlayer();
      set({ isCallActive: false, characterId: null, phase: "idle", transcript: "", errorMessage: null });
    },

    startListening: () => {
      if (!get().isCallActive || get().phase === "listening") return;
      set({ phase: "listening", transcript: "", errorMessage: null });
      ExpoSpeechRecognitionModule.start({
        lang: SPEECH_LOCALE,
        interimResults: true,
        continuous: false,
      });
    },

    stopListening: () => {
      if (get().phase !== "listening") return;
      ExpoSpeechRecognitionModule.stop();
    },
  };
});
