import { NativeModules, Platform } from "react-native";

type HapticNotifierNativeModule = {
  notify: (title: string, body: string) => void;
};

const HapticNotifier = NativeModules.HapticNotifier as HapticNotifierNativeModule | undefined;

/**
 * Haptic feedback + a foreground local notification banner for a newly
 * arrived assistant message (PRD 3.6/3.7 — Swift `RCTBridgeModule`,
 * `NativeModules.HapticNotifier.notify()`). iOS-only, Android is out of
 * scope. No-ops if the native module isn't linked in this build yet
 * (e.g. before running `expo prebuild`, or in Expo Go which can't load
 * custom native modules at all).
 */
export function notifyNewMessage(title: string, body: string): void {
  if (Platform.OS !== "ios" || !HapticNotifier) return;
  HapticNotifier.notify(title, body);
}
