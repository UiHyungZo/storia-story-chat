/**
 * Web shim for `./registerPushToken` — `@react-native-firebase/messaging` has no
 * web implementation at all (`getMessaging()` throws there). Web push would need
 * the separate `firebase` JS SDK + a service worker, which is out of scope here;
 * this just no-ops, same externally-visible behavior as the native version when
 * Firebase isn't configured.
 */
export async function registerForPushNotifications(): Promise<void> {
  // no-op on web
}
