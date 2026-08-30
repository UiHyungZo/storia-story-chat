import { ActivityIndicator, Modal, Pressable, StyleSheet, Text, View } from "react-native";
import { useVoiceCallStore } from "../store/useVoiceCallStore";
import { avatarColorFor } from "../utils/avatarColor";

type Props = {
  characterId: number;
  characterName: string;
};

const PHASE_LABEL: Record<string, string> = {
  idle: "마이크를 눌러 말해보세요",
  listening: "듣고 있어요… 다 말했으면 다시 눌러주세요",
  thinking: "생각하는 중…",
  speaking: "말하는 중…",
  error: "문제가 발생했어요",
};

export function VoiceCallOverlay({ characterId, characterName }: Props) {
  const isCallActive = useVoiceCallStore((state) => state.isCallActive);
  const phase = useVoiceCallStore((state) => state.phase);
  const mode = useVoiceCallStore((state) => state.mode);
  const agentSpeaking = useVoiceCallStore((state) => state.agentSpeaking);
  const errorMessage = useVoiceCallStore((state) => state.errorMessage);
  const startListening = useVoiceCallStore((state) => state.startListening);
  const stopListening = useVoiceCallStore((state) => state.stopListening);
  const endCall = useVoiceCallStore((state) => state.endCall);

  const agentMode = mode === "agent";
  const listening = phase === "listening";
  // Turn mode disables the mic button while a reply is being fetched/played.
  const busy = !agentMode && (phase === "thinking" || phase === "speaking");
  const showSpinner = busy || (agentMode && agentSpeaking);

  // Agent mode is full-duplex once the mic is live: the worker's VAD does turn-
  // taking, so the mic button is only shown to *start* (publish once) and then
  // hidden. Turn mode toggles 🎤/■ every turn.
  const showMicButton = !agentMode || !listening;

  let status: string;
  if (errorMessage) {
    status = errorMessage;
  } else if (agentMode) {
    status = !listening
      ? "마이크를 눌러 통화를 시작하세요"
      : agentSpeaking
        ? `${characterName} 말하는 중…`
        : "듣고 있어요 — 자유롭게 말하세요";
  } else {
    status = PHASE_LABEL[phase];
  }

  return (
    <Modal visible={isCallActive} animationType="fade" transparent>
      <View style={styles.backdrop}>
        <View style={[styles.avatar, { backgroundColor: avatarColorFor(characterId) }]} />
        <Text style={styles.name}>{characterName}</Text>
        {agentMode && <Text style={styles.badge}>실시간 통화</Text>}
        <Text style={styles.status}>{status}</Text>
        {showSpinner && <ActivityIndicator style={styles.spinner} color="#FFFFFF" />}

        <View style={styles.controls}>
          {showMicButton && (
            <Pressable
              style={[styles.micButton, listening && styles.micButtonActive]}
              onPress={() => (listening ? stopListening() : startListening())}
              disabled={busy}
            >
              <Text style={styles.micButtonText}>{listening ? "■" : "🎤"}</Text>
            </Pressable>
          )}
          <Pressable style={styles.endButton} onPress={endCall}>
            <Text style={styles.endButtonText}>통화 종료</Text>
          </Pressable>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: {
    flex: 1,
    backgroundColor: "#111827F2",
    alignItems: "center",
    justifyContent: "center",
    padding: 24,
    gap: 12,
  },
  avatar: {
    width: 96,
    height: 96,
    borderRadius: 48,
  },
  name: {
    fontSize: 22,
    fontWeight: "700",
    color: "#FFFFFF",
  },
  badge: {
    fontSize: 12,
    fontWeight: "600",
    color: "#111827",
    backgroundColor: "#A7F3D0",
    paddingHorizontal: 10,
    paddingVertical: 3,
    borderRadius: 10,
    overflow: "hidden",
  },
  status: {
    fontSize: 15,
    color: "#D1D5DB",
  },
  spinner: {
    marginTop: 8,
  },
  controls: {
    marginTop: 40,
    alignItems: "center",
    gap: 20,
  },
  micButton: {
    width: 72,
    height: 72,
    borderRadius: 36,
    backgroundColor: "#3B82F6",
    alignItems: "center",
    justifyContent: "center",
  },
  micButtonActive: {
    backgroundColor: "#DC2626",
  },
  micButtonText: {
    fontSize: 28,
  },
  endButton: {
    paddingHorizontal: 20,
    paddingVertical: 10,
    borderRadius: 20,
    backgroundColor: "#374151",
  },
  endButtonText: {
    color: "#FFFFFF",
    fontWeight: "600",
  },
});
