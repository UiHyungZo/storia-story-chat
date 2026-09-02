import { StyleSheet, Text, View } from "react-native";
import { Message } from "../types";

type Props = {
  message: Message;
};

export function MessageBubble({ message }: Props) {
  const isUser = message.role === "user";
  return (
    <View style={[styles.row, isUser ? styles.rowUser : styles.rowAssistant]}>
      <View
        testID={isUser ? "message-bubble-user" : "message-bubble-assistant"}
        style={[styles.bubble, isUser ? styles.bubbleUser : styles.bubbleAssistant]}
      >
        <Text style={isUser ? styles.textUser : styles.textAssistant}>{message.content}</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: "row",
    paddingHorizontal: 12,
    marginVertical: 4,
  },
  rowUser: {
    justifyContent: "flex-end",
  },
  rowAssistant: {
    justifyContent: "flex-start",
  },
  bubble: {
    maxWidth: "78%",
    paddingVertical: 10,
    paddingHorizontal: 14,
    borderRadius: 18,
  },
  bubbleUser: {
    backgroundColor: "#3B82F6",
    borderBottomRightRadius: 4,
  },
  bubbleAssistant: {
    backgroundColor: "#E5E7EB",
    borderBottomLeftRadius: 4,
  },
  textUser: {
    color: "#FFFFFF",
    fontSize: 15,
  },
  textAssistant: {
    color: "#111827",
    fontSize: 15,
  },
});
