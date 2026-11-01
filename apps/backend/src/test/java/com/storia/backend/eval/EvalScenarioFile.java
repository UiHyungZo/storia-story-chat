package com.storia.backend.eval;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record EvalScenarioFile(
        @JsonProperty("prompt_version") String promptVersion,
        @JsonProperty("scenarios") List<EvalScenario> scenarios) {
}
