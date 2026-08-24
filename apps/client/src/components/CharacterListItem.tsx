import { Pressable, StyleSheet, Text, View } from "react-native";
import { Character } from "../types";
import { avatarColorFor } from "../utils/avatarColor";

type Props = {
  character: Character;
  onPress: () => void;
};

export function CharacterListItem({ character, onPress }: Props) {
  return (
    <Pressable style={styles.container} onPress={onPress}>
      <View style={[styles.avatar, { backgroundColor: avatarColorFor(character.id) }]} />
      <View style={styles.textContainer}>
        <Text style={styles.name}>{character.name}</Text>
        <Text style={styles.preview} numberOfLines={1}>
          {character.concept}
        </Text>
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  container: {
    flexDirection: "row",
    alignItems: "center",
    paddingVertical: 12,
    paddingHorizontal: 16,
    gap: 12,
  },
  avatar: {
    width: 48,
    height: 48,
    borderRadius: 24,
  },
  textContainer: {
    flex: 1,
  },
  name: {
    fontSize: 16,
    fontWeight: "600",
    color: "#111827",
  },
  preview: {
    fontSize: 14,
    color: "#6B7280",
    marginTop: 2,
  },
});
