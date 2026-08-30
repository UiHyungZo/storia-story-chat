import { AudioPlayer, createAudioPlayer, setAudioModeAsync } from "expo-audio";
import { LocalTrackPublication, Room, Track } from "livekit-client";
import { create } from "zustand";
import { getTurnStatus, requestCallToken, startTurnEgress } from "../api/calls";
import { getMessageAudioUrl, isMessageAudioAvailable } from "../api/tts";

const POLL_INTERVAL_MS = 600;
// A turn runs STT -> Gemini -> TTS server-side. gemini-3.6-flash is a reasoning model
// that regularly takes 30-45s on a character prompt (see GeminiService.CHUNK_TIMEOUT,
// which was bumped to 60s for the same reason), so a 20s budget times out the happy path.
const POLL_TIMEOUT_MS = 75000;
// A synthesized character reply is at most a few hundred characters of Korean TTS
// (~30-60s of audio). If playback hasn't reported didJustFinish by then, treat the
// turn as done rather than pinning the call in "speaking" forever.
const PLAYBACK_MAX_MS = 90000;

export type CallPhase = "idle" | "listening" | "thinking" | "speaking" | "error";

type VoiceCallStore = {
  isCallActive: boolean;
  characterId: number | null;
  phase: CallPhase;
  errorMessage: string | null;
  startCall: (characterId: number) => Promise<void>;
  endCall: () => void;
  startListening: () => Promise<void>;
  stopListening: () => Promise<void>;
};

let room: Room | null = null;
let roomName: string | null = null;
let currentTurnId: string | null = null;
let micPublication: LocalTrackPublication | null = null;
let player: AudioPlayer | null = null;

function stopAndReleasePlayer(): void {
  if (!player) return;
  player.pause();
  player.remove();
  player = null;
}

function teardownRoom(): void {
  room?.disconnect();
  room = null;
  roomName = null;
  currentTurnId = null;
  micPublication = null;
}

/**
 * "축소판 A안" (docs/decisions.md ADR-004 갱신 항목): 클라이언트가 LiveKit으로 실제
 * WebRTC 오디오를 서버까지 보내고(마이크 트랙 publish → 백엔드가 Track Egress 시작),
 * 서버가 그 오디오를 배치 STT → 기존 Gemini/TTS 파이프라인(B안)에 흘려보낸다. 응답은
 * B안과 동일하게 오디오 URL로 돌아옴 — 서버가 합성 음성을 WebRTC로 직접 되쏘는 완전한
 * 양방향 실시간은 범위 밖. 턴제(풀 듀플렉스 아님): idle → listening → thinking →
 * speaking, 재생이 끝나야 다음 턴을 들을 수 있음.
 */
export const useVoiceCallStore = create<VoiceCallStore>((set, get) => {
  async function playAssistantAudio(messageId: number): Promise<void> {
    const available = await isMessageAudioAvailable(messageId);
    if (!available) {
      // TTS not configured/failed — reply text is already in the chat history,
      // just return to idle so the user can start the next turn.
      set((state) => (state.isCallActive ? { phase: "idle" } : state));
      return;
    }

    set({ phase: "speaking" });
    stopAndReleasePlayer();

    // The turn we just finished left the iOS audio session in playAndRecord (LiveKit
    // mic capture), which routes playback to the quiet earpiece and honours the ring
    // switch. Flip it back to a playback session so the reply comes out the speaker.
    try {
      await setAudioModeAsync({ playsInSilentMode: true, allowsRecording: false });
    } catch {
      // non-fatal — fall through and try to play anyway
    }

    player = createAudioPlayer(getMessageAudioUrl(messageId));

    await new Promise<void>((resolve) => {
      // Don't hang the call forever if the clip never loads or never fires
      // didJustFinish (bad source, dropped connection mid-stream).
      const safety = setTimeout(resolve, PLAYBACK_MAX_MS);
      const subscription = player!.addListener("playbackStatusUpdate", (status) => {
        if (status.didJustFinish) {
          clearTimeout(safety);
          subscription.remove();
          resolve();
        }
      });
      player!.play();
    });

    stopAndReleasePlayer();
    set((state) => (state.isCallActive ? { phase: "idle" } : state));
  }

  async function pollTurn(turnId: string): Promise<void> {
    const deadline = Date.now() + POLL_TIMEOUT_MS;
    while (Date.now() < deadline) {
      if (!get().isCallActive || currentTurnId !== null) {
        // Call ended, or a newer turn started — abandon this poll silently.
        return;
      }
      const status = await getTurnStatus(turnId);
      if (status.status === "done" && status.assistantMessageId != null) {
        await playAssistantAudio(status.assistantMessageId);
        return;
      }
      if (status.status === "error") {
        set({ phase: "error", errorMessage: status.errorMessage ?? "응답 생성에 실패했습니다." });
        return;
      }
      await new Promise((resolve) => setTimeout(resolve, POLL_INTERVAL_MS));
    }
    set((state) => (state.isCallActive ? { phase: "error", errorMessage: "응답이 너무 오래 걸려요." } : state));
  }

  return {
    isCallActive: false,
    characterId: null,
    phase: "idle",
    errorMessage: null,

    startCall: async (characterId) => {
      set({ isCallActive: true, characterId, phase: "idle", errorMessage: null });
      try {
        const tokenResponse = await requestCallToken(characterId);
        roomName = tokenResponse.roomName;
        room = new Room();
        await room.connect(tokenResponse.url, tokenResponse.token);
      } catch (error) {
        set({ phase: "error", errorMessage: error instanceof Error ? error.message : String(error) });
      }
    },

    endCall: () => {
      stopAndReleasePlayer();
      teardownRoom();
      set({ isCallActive: false, characterId: null, phase: "idle", errorMessage: null });
    },

    startListening: async () => {
      const { isCallActive, characterId, phase } = get();
      if (!isCallActive || !room || !roomName || !characterId || phase === "listening") return;

      set({ phase: "listening", errorMessage: null });
      try {
        const publication = await room.localParticipant.setMicrophoneEnabled(true);
        if (!publication?.trackSid) {
          throw new Error("마이크 트랙을 시작하지 못했어요.");
        }
        micPublication = publication;
        const { turnId } = await startTurnEgress(characterId, roomName, publication.trackSid);
        currentTurnId = turnId;
      } catch (error) {
        set({ phase: "error", errorMessage: error instanceof Error ? error.message : String(error) });
      }
    },

    stopListening: async () => {
      if (get().phase !== "listening" || !room) return;
      set({ phase: "thinking" });
      // Unpublishing (not just muting) the track is what ends the backend's Track
      // Egress connection, which is the "this turn's audio is complete" signal.
      // NOTE: livekit-client's setMicrophoneEnabled(false) only *mutes* an audio
      // track (it unpublishes for screen-share only), so egress would keep running
      // and the turn would never complete — we must unpublish explicitly.
      const publication =
        micPublication ?? room.localParticipant.getTrackPublication(Track.Source.Microphone);
      micPublication = null;
      if (publication?.track) {
        await room.localParticipant.unpublishTrack(publication.track, true);
      }
      const turnId = currentTurnId;
      currentTurnId = null;
      if (turnId) {
        await pollTurn(turnId);
      }
    },
  };
});
