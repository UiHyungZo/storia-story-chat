import { NativeModules } from "react-native";

type HapticNotifierNativeModule = {
  notify: (title: string, body: string) => void;
};

const HapticNotifier = NativeModules.HapticNotifier as HapticNotifierNativeModule | undefined;

/**
 * Haptic feedback + a local notification banner for a newly arrived
 * assistant message (PRD 3.6/3.7). iOS: classic `RCTBridgeModule` (Swift,
 * `ios/HapticNotifierModule.swift`). Android: classic
 * `ReactContextBaseJavaModule` (Kotlin, `android/.../HapticNotifierModule.kt`).
 * Both expose the same `NativeModules.HapticNotifier.notify()` bridge name.
 * No-ops if the native module isn't linked in this build yet (e.g. before
 * running `expo prebuild`, or in Expo Go which can't load custom native
 * modules at all).
 */
export function notifyNewMessage(title: string, body: string): void {
  if (!HapticNotifier) return;
  HapticNotifier.notify(title, body);
}
