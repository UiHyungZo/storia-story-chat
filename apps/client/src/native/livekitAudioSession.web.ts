/**
 * Web shim for `./livekitAudioSession` — both `@livekit/react-native`'s
 * `registerGlobals()` (patches in RN-WebRTC's native globals) and `AudioSession`
 * (routes iOS/Android audio sessions) are RN-only concepts. Browsers already have
 * WebRTC built in and handle audio output routing themselves, so both are no-ops
 * here. `livekit-client` itself (used directly in useVoiceCallStore) is the
 * browser SDK and needs none of this.
 */
export function registerWebrtcGlobals(): void {
  // no-op on web
}

export const audioSession = {
  start: () => Promise.resolve(),
  stop: () => Promise.resolve(),
};
