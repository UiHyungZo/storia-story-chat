import * as Sentry from "@sentry/react-native";

/**
 * Native (iOS/Android) implementation — re-exports `@sentry/react-native`. The
 * `.web.ts` sibling stubs both out, since that package has no official web
 * support. See that file for the swap-in path if web error tracking is needed
 * later.
 */
export const init = Sentry.init;
export const wrap = Sentry.wrap;
