import { NativeStackScreenProps } from "@react-navigation/native-stack";
import { useEffect } from "react";
import { ActivityIndicator, FlatList, Pressable, SafeAreaView, StyleSheet, Text } from "react-native";
import { CharacterListItem } from "../components/CharacterListItem";
import { useCharacterStore } from "../store/useCharacterStore";
import { RootStackParamList } from "../types";

type Props = NativeStackScreenProps<RootStackParamList, "CharacterList">;

export function CharacterListScreen({ navigation }: Props) {
  const characters = useCharacterStore((state) => state.characters);
  const isLoading = useCharacterStore((state) => state.isLoading);
  const error = useCharacterStore((state) => state.error);
  const loadCharacters = useCharacterStore((state) => state.loadCharacters);

  useEffect(() => {
    loadCharacters();
  }, [loadCharacters]);

  return (
    <SafeAreaView style={styles.container}>
      <Text style={styles.title}>Storia</Text>
      {isLoading && characters.length === 0 && <ActivityIndicator style={styles.centerBlock} />}
      {error && (
        <Pressable style={styles.errorBanner} onPress={() => loadCharacters()} disabled={isLoading}>
          <Text style={styles.error}>{error}</Text>
          <Text style={styles.retryText}>{isLoading ? "재시도 중…" : "다시 시도"}</Text>
        </Pressable>
      )}
      <FlatList
        data={characters}
        keyExtractor={(item) => item.id.toString()}
        renderItem={({ item }) => (
          <CharacterListItem
            character={item}
            onPress={() => navigation.navigate("ChatRoom", { characterId: item.id })}
          />
        )}
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#FFFFFF",
  },
  title: {
    fontSize: 24,
    fontWeight: "700",
    paddingHorizontal: 16,
    paddingVertical: 12,
    color: "#111827",
  },
  centerBlock: {
    marginTop: 24,
  },
  errorBanner: {
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
});
