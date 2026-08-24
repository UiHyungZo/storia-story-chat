import { ActivityIndicator, Modal, Pressable, StyleSheet, Text, View } from "react-native";
import { useVoiceCallStore } from "../store/useVoiceCallStore";
import { avatarColorFor } from "../utils/avatarColor";

type Props = {
  characterId: number;
  characterName: string;
};

const PHASE_LABEL: Record<string, string> = {
  idle: "마이크를 눌러 말해보세요",
  listening: "듣고 있어요…",
  thinking: "생각하는 중…",
  speaking: "말하는 중…",
  error: "문제가 발생했어요",
};

export function VoiceCallOverlay({ characterId, characterName }: Props) {
  const isCallActive = useVoiceCallStore((state) => state.isCallActive);
  const phase = useVoiceCallStore((state) => state.phase);
  const transcript = useVoiceCallStore((state) => state.transcript);
  const errorMessage = useVoiceCallStore((state) => state.errorMessage);
  const startListening = useVoiceCallStore((state) => state.startListening);
  const stopListening = useVoiceCallStore((state) => state.stopListening);
  const endCall = useVoiceCallStore((state) => state.endCall);

  return (
    <Modal visible={isCallActive} animationType="fade" transparent>
      <View style={styles.backdrop}>
        <View style={[styles.avatar, { backgroundColor: avatarColorFor(characterId) }]} />
        <Text style={styles.name}>{characterName}</Text>
        <Text style={styles.status}>{errorMessage ?? PHASE_LABEL[phase]}</Text>
        {phase === "listening" && transcript.length > 0 && (
          <Text style={styles.transcript} numberOfLines={3}>
            "{transcript}"
          </Text>
        )}
        {(phase === "thinking" || phase === "speaking") && (
          <ActivityIndicator style={styles.spinner} color="#FFFFFF" />
        )}

        <View style={styles.controls}>
          <Pressable
            style={[styles.micButton, phase === "listening" && styles.micButtonActive]}
            onPress={() => (phase === "listening" ? stopListening() : startListening())}
            disabled={phase === "thinking" || phase === "speaking"}
          >
            <Text style={styles.micButtonText}>{phase === "listening" ? "■" : "🎤"}</Text>
          </Pressable>
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
  status: {
    fontSize: 15,
    color: "#D1D5DB",
  },
  transcript: {
    fontSize: 14,
    color: "#9CA3AF",
    fontStyle: "italic",
    textAlign: "center",
    paddingHorizontal: 32,
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
