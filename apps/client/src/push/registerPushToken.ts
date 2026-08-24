import { AuthorizationStatus, getMessaging, getToken, onTokenRefresh, requestPermission } from "@react-native-firebase/messaging";
import { registerDeviceToken } from "../api/devices";

/**
 * Best-effort FCM registration — never throws, since a missing/incomplete
 * Firebase setup (or a user denying the permission prompt) shouldn't block
 * app start. Same graceful-degradation posture as the backend's optional
 * integrations.
 */
export async function registerForPushNotifications(): Promise<void> {
  try {
    const messaging = getMessaging();
    const authStatus = await requestPermission(messaging);
    const granted =
      authStatus === AuthorizationStatus.AUTHORIZED || authStatus === AuthorizationStatus.PROVISIONAL;
    if (!granted) {
      return;
    }

    const token = await getToken(messaging);
    await registerDeviceToken(token);

    onTokenRefresh(messaging, (refreshedToken) => {
      registerDeviceToken(refreshedToken).catch(() => {});
    });
  } catch {
    // no-op — see doc comment above
  }
}
