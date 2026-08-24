import { Platform } from "react-native";

/**
 * "localhost" resolves to the device itself, not the dev machine, on Android
 * emulators (needs 10.0.2.2) and doesn't resolve at all on physical devices
 * (needs the dev machine's LAN IP). Override with EXPO_PUBLIC_API_BASE_URL
 * (e.g. in a .env file) when running on a physical device.
 */
const DEFAULT_LOCAL_HOST = Platform.OS === "android" ? "10.0.2.2" : "localhost";

export const API_BASE_URL =
  process.env.EXPO_PUBLIC_API_BASE_URL ?? `http://${DEFAULT_LOCAL_HOST}:8080`;
