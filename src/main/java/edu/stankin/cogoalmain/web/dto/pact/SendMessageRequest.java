package edu.stankin.cogoalmain.web.dto.pact;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(@NotBlank @Size(max = 2000) String text) {
}
