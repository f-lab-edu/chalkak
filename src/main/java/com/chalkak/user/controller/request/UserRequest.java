package com.chalkak.user.controller.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UserRequest(
    @NotBlank
    @Email
    String email,

    @NotBlank
    String password,

    @NotBlank
    @Pattern(regexp = "^01\\d-\\d{3,4}-\\d{4}$")
    String phone
) {
}
