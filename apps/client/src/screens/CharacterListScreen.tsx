import { NativeStackScreenProps } from "@react-navigation/native-stack";
import { FlatList, SafeAreaView, StyleSheet, Text } from "react-native";
import { CharacterListItem } from "../components/CharacterListItem";
import { useCharacterStore } from "../store/useCharacterStore";
import { RootStackParamList } from "../types";

type Props = NativeStackScreenProps<RootStackParamList, "CharacterList">;

export function CharacterListScreen({ navigation }: Props) {
  const characters = useCharacterStore((state) => state.characters);

  return (
    <SafeAreaView style={styles.container}>
      <Text style={styles.title}>Storia</Text>
      <FlatList
        data={characters}
        keyExtractor={(item) => item.id}
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
});
