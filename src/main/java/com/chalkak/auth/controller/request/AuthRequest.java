package com.chalkak.auth.controller.request;

import jakarta.validation.constraints.NotBlank;

public record AuthRequest(
    @NotBlank
    String email,

    @NotBlank
    String password
) {
}
