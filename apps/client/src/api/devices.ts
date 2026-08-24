import { apiPut } from "./client";

export function registerDeviceToken(token: string): Promise<void> {
  return apiPut<void>("/api/devices/token", { token });
}
