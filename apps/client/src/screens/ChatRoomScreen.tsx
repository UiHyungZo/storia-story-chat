import { NativeStackScreenProps } from "@react-navigation/native-stack";
import { useLayoutEffect, useState } from "react";
import {
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
import { RootStackParamList } from "../types";

type Props = NativeStackScreenProps<RootStackParamList, "ChatRoom">;

export function ChatRoomScreen({ route, navigation }: Props) {
  const { characterId } = route.params;
  const character = useCharacterStore((state) => state.getCharacterById(characterId));
  const messages = useConversationStore((state) => state.getMessages(characterId));
  const sendMessage = useConversationStore((state) => state.sendMessage);
  const [draft, setDraft] = useState("");

  useLayoutEffect(() => {
    navigation.setOptions({ title: character?.name ?? "Chat" });
  }, [navigation, character]);

  const handleSend = () => {
    const trimmed = draft.trim();
    if (!trimmed) return;
    sendMessage(characterId, trimmed);
    setDraft("");
  };

  return (
    <SafeAreaView style={styles.container}>
      <KeyboardAvoidingView
        style={styles.flex}
        behavior={Platform.OS === "ios" ? "padding" : undefined}
        keyboardVerticalOffset={80}
      >
        <FlatList
          style={styles.flex}
          data={[...messages].reverse()}
          keyExtractor={(item) => item.id}
          inverted
          renderItem={({ item }) => <MessageBubble message={item} />}
        />
        <View style={styles.inputRow}>
          <TextInput
            style={styles.input}
            value={draft}
            onChangeText={setDraft}
            placeholder="메시지를 입력하세요"
            multiline
          />
          <Pressable style={styles.sendButton} onPress={handleSend}>
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
