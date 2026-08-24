import { registerGlobals } from "@livekit/react-native";
import { StatusBar } from "expo-status-bar";
import { RootNavigator } from "./src/navigation/RootNavigator";

// Must run once, before any LiveKit usage — sets up WebRTC globals for RN.
registerGlobals();

export default function App() {
  return (
    <>
      <RootNavigator />
      <StatusBar style="auto" />
    </>
  );
}
