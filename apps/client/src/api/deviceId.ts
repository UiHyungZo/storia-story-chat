import AsyncStorage from "@react-native-async-storage/async-storage";

const DEVICE_ID_STORAGE_KEY = "storia:deviceId";

let cachedDeviceId: string | null = null;

function generateUuidV4(): string {
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (char) => {
    const random = (Math.random() * 16) | 0;
    const value = char === "x" ? random : (random & 0x3) | 0x8;
    return value.toString(16);
  });
}

export async function getDeviceId(): Promise<string> {
  if (cachedDeviceId) {
    return cachedDeviceId;
  }

  const stored = await AsyncStorage.getItem(DEVICE_ID_STORAGE_KEY);
  if (stored) {
    cachedDeviceId = stored;
    return stored;
  }

  const generated = generateUuidV4();
  await AsyncStorage.setItem(DEVICE_ID_STORAGE_KEY, generated);
  cachedDeviceId = generated;
  return generated;
}
