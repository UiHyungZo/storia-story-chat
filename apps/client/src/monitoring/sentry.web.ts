/**
 * Web shim for `./sentry` — `@sentry/react-native` has no official web support
 * (no `.web.*` entry in the package). `init` becomes a no-op, `wrap` an identity
 * function, matching the existing "DSN not set -> silently disabled" posture in
 * App.tsx (`enabled: Boolean(sentryDsn)`). Swap in `@sentry/react` here if web
 * error tracking is needed later.
 */
export function init(_options?: unknown): void {
  // no-op on web
}

export function wrap<T>(component: T): T {
  return component;
}
