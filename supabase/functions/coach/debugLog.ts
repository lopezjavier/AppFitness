// COACH_DEBUG diagnostics (T-212). When on, the function logs which rule rejected an answer and the provider's
// stop_reason, so a coach_invalid_output can be diagnosed. Only enumerated codes, numeric paths, numbers and the
// stop_reason string are logged: never the prompt, the model text, exercise names, user data, tokens or secrets.

import type { AttemptInfo, RetryOptions } from "../_shared/anthropic.ts";

/** Per request, like `FOOD_PHOTO_DEBUG`: any non-blank value turns it on. */
export function envDebug(): boolean {
    return (Deno.env.get("COACH_DEBUG") ?? "").trim() !== "";
}

/** Adds one `coach: debug` warning per attempt to a retry config; a no-op when debug is off. */
export function withDebug(
    kind: string,
    retry: RetryOptions | undefined,
    debug: boolean,
    log: (message: string, data: Record<string, unknown>) => void =
        console.warn,
): RetryOptions | undefined {
    if (!debug) return retry;
    return {
        ...retry,
        onAttempt: (info: AttemptInfo) => {
            retry?.onAttempt?.(info);
            log("coach: debug", {
                kind,
                attempt: info.attempt,
                stop_reason: info.stopReason ?? null,
                output_tokens: info.outputTokens,
                valid: info.valid,
                codes: info.codes,
            });
        },
    };
}
