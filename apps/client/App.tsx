import { registerGlobals } from "@livekit/react-native";
import * as Sentry from "@sentry/react-native";
import { StatusBar } from "expo-status-bar";
import { RootNavigator } from "./src/navigation/RootNavigator";

// Must run once, before any LiveKit usage — sets up WebRTC globals for RN.
registerGlobals();

// No-ops (doesn't send events, doesn't throw) when the DSN isn't set — same
// graceful-degradation posture as the backend's *Properties#isConfigured() pattern.
const sentryDsn = process.env.EXPO_PUBLIC_SENTRY_DSN;
Sentry.init({
  dsn: sentryDsn,
  enabled: Boolean(sentryDsn),
  tracesSampleRate: 1.0,
});

function App() {
  return (
    <>
      <RootNavigator />
      <StatusBar style="auto" />
    </>
  );
}

export default Sentry.wrap(App);
