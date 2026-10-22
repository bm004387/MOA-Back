package com.buc.moa.auth.vo.record;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 인증 완료 후 새 비밀번호 저장 요청입니다. */
public record PasswordResetRequest(
        @NotBlank @Size(max = 50) String usrId,
        @NotBlank @Pattern(regexp = "^01[0-9]{8,9}$") String hpNo,
        @NotBlank @Size(min = 8, max = 72) String newPassword,
        @NotBlank @Size(max = 72) String confirmPassword
) {
}
