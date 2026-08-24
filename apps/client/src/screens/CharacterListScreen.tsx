import { NativeStackScreenProps } from "@react-navigation/native-stack";
import { useEffect } from "react";
import { ActivityIndicator, FlatList, SafeAreaView, StyleSheet, Text } from "react-native";
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
      {error && <Text style={styles.error}>{error}</Text>}
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
  error: {
    paddingHorizontal: 16,
    paddingBottom: 8,
    color: "#DC2626",
  },
});
