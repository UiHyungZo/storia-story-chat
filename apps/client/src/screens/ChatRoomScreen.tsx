import { NativeStackScreenProps } from "@react-navigation/native-stack";
import { useEffect, useLayoutEffect, useMemo, useState } from "react";
import {
  ActivityIndicator,
  FlatList,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  SafeAreaView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from "react-native";
import { MessageBubble } from "../components/MessageBubble";
import { useCharacterStore } from "../store/useCharacterStore";
import { useConversationStore } from "../store/useConversationStore";
import { Message, RootStackParamList } from "../types";

type Props = NativeStackScreenProps<RootStackParamList, "ChatRoom">;

const STREAMING_PLACEHOLDER_ID = Number.MIN_SAFE_INTEGER;

export function ChatRoomScreen({ route, navigation }: Props) {
  const { characterId } = route.params;
  const character = useCharacterStore((state) => state.getCharacterById(characterId));
  const messages = useConversationStore((state) => state.getMessages(characterId));
  const streamingContent = useConversationStore((state) => state.getStreamingContent(characterId));
  const connectionStatus = useConversationStore((state) => state.getConnectionStatus(characterId));
  const isLoading = useConversationStore((state) => state.isLoading);
  const error = useConversationStore((state) => state.error);
  const loadMessages = useConversationStore((state) => state.loadMessages);
  const disconnect = useConversationStore((state) => state.disconnect);
  const sendMessage = useConversationStore((state) => state.sendMessage);
  const [draft, setDraft] = useState("");
  const [isSending, setIsSending] = useState(false);
  const [sendError, setSendError] = useState<{ content: string; message: string } | null>(null);

  useLayoutEffect(() => {
    navigation.setOptions({ title: character?.name ?? "Chat" });
  }, [navigation, character]);

  useEffect(() => {
    loadMessages(characterId);
    return () => disconnect(characterId);
  }, [characterId, loadMessages, disconnect]);

  const displayMessages = useMemo(() => {
    const reversed = [...messages].reverse();
    if (streamingContent) {
      const placeholder: Message = {
        id: STREAMING_PLACEHOLDER_ID,
        role: "assistant",
        content: streamingContent,
        createdAt: new Date().toISOString(),
      };
      reversed.unshift(placeholder);
    }
    return reversed;
  }, [messages, streamingContent]);

  const handleSend = async (retryContent?: string) => {
    const trimmed = (retryContent ?? draft).trim();
    if (!trimmed || isSending) return;
    if (!retryContent) setDraft("");
    setSendError(null);
    setIsSending(true);
    try {
      await sendMessage(characterId, trimmed);
    } catch (err) {
      setSendError({ content: trimmed, message: err instanceof Error ? err.message : String(err) });
    } finally {
      setIsSending(false);
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      <KeyboardAvoidingView
        style={styles.flex}
        behavior={Platform.OS === "ios" ? "padding" : undefined}
        keyboardVerticalOffset={80}
      >
        {isLoading && messages.length === 0 && <ActivityIndicator style={styles.centerBlock} />}
        {error && (
          <Pressable
            style={styles.errorBanner}
            onPress={() => loadMessages(characterId)}
            disabled={isLoading}
          >
            <Text style={styles.error}>{error}</Text>
            <Text style={styles.retryText}>{isLoading ? "재시도 중…" : "다시 시도"}</Text>
          </Pressable>
        )}
        {connectionStatus === "reconnecting" && (
          <View style={styles.statusBanner}>
            <ActivityIndicator size="small" color="#92400E" />
            <Text style={styles.statusText}>연결이 끊겨 재연결 중입니다… (그동안 메시지는 일반 방식으로 전송돼요)</Text>
          </View>
        )}
        <FlatList
          style={styles.flex}
          data={displayMessages}
          keyExtractor={(item) => item.id.toString()}
          inverted
          renderItem={({ item }) => <MessageBubble message={item} />}
        />
        {sendError && (
          <Pressable style={styles.sendErrorBanner} onPress={() => handleSend(sendError.content)}>
            <Text style={styles.error} numberOfLines={2}>
              메시지 전송 실패: {sendError.message}
            </Text>
            <Text style={styles.retryText}>다시 시도</Text>
          </Pressable>
        )}
        <View style={styles.inputRow}>
          <TextInput
            style={styles.input}
            value={draft}
            onChangeText={setDraft}
            placeholder="메시지를 입력하세요"
            multiline
          />
          <Pressable style={styles.sendButton} onPress={() => handleSend()} disabled={isSending}>
            <Text style={styles.sendButtonText}>전송</Text>
          </Pressable>
        </View>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#FFFFFF",
  },
  flex: {
    flex: 1,
  },
  centerBlock: {
    marginTop: 24,
  },
  errorBanner: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    marginHorizontal: 16,
    marginVertical: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    borderRadius: 8,
    backgroundColor: "#FEE2E2",
  },
  sendErrorBanner: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    marginHorizontal: 16,
    marginBottom: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    borderRadius: 8,
    backgroundColor: "#FEE2E2",
  },
  error: {
    flex: 1,
    color: "#DC2626",
  },
  retryText: {
    marginLeft: 12,
    color: "#B91C1C",
    fontWeight: "600",
  },
  statusBanner: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    marginHorizontal: 16,
    marginVertical: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    borderRadius: 8,
    backgroundColor: "#FEF3C7",
  },
  statusText: {
    flex: 1,
    color: "#92400E",
    fontSize: 13,
  },
  inputRow: {
    flexDirection: "row",
    alignItems: "flex-end",
    padding: 12,
    gap: 8,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: "#E5E7EB",
  },
  input: {
    flex: 1,
    maxHeight: 120,
    paddingHorizontal: 14,
    paddingVertical: 10,
    borderRadius: 20,
    backgroundColor: "#F3F4F6",
    fontSize: 15,
  },
  sendButton: {
    paddingHorizontal: 16,
    paddingVertical: 10,
    borderRadius: 20,
    backgroundColor: "#3B82F6",
  },
  sendButtonText: {
    color: "#FFFFFF",
    fontWeight: "600",
  },
});
