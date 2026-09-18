import { AudioSession, registerGlobals } from "@livekit/react-native";

/**
 * Native (iOS/Android) implementation — thin re-export of `@livekit/react-native`.
 * The `.web.ts` sibling of this file no-ops all three, since browsers ship WebRTC
 * globals natively and manage audio routing themselves. See that file for why this
 * indirection exists at all.
 */
export function registerWebrtcGlobals(): void {
  registerGlobals();
}

export const audioSession = {
  start: () => AudioSession.startAudioSession(),
  stop: () => AudioSession.stopAudioSession(),
};
