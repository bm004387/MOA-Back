package com.buc.moa.user.vo.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordVerifyRequest(
        @NotBlank @Size(max = 72) String currentPassword) {
}
