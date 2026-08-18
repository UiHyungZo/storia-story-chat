import { NavigationContainer } from "@react-navigation/native";
import { createNativeStackNavigator } from "@react-navigation/native-stack";
import { CharacterListScreen } from "../screens/CharacterListScreen";
import { ChatRoomScreen } from "../screens/ChatRoomScreen";
import { RootStackParamList } from "../types";

const Stack = createNativeStackNavigator<RootStackParamList>();

export function RootNavigator() {
  return (
    <NavigationContainer>
      <Stack.Navigator initialRouteName="CharacterList">
        <Stack.Screen
          name="CharacterList"
          component={CharacterListScreen}
          options={{ title: "Storia" }}
        />
        <Stack.Screen name="ChatRoom" component={ChatRoomScreen} />
      </Stack.Navigator>
    </NavigationContainer>
  );
}
