package com.storia.backend.eval;

import java.util.List;

public record EvalResult(
        String id,
        List<String> tags,
        Verdict verdict,
        String reason,
        List<String> failedChecks,
        String reply,
        String review,
        String expectedBehavior,
        List<String> rubric) {
}
