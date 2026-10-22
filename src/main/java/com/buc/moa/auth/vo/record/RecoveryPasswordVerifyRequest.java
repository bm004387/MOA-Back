package com.buc.moa.auth.vo.record;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 비밀번호 재설정용 휴대폰 인증 요청입니다. */
public record RecoveryPasswordVerifyRequest(
        @NotBlank @Size(max = 50) String usrId,
        @NotBlank @Pattern(regexp = "^01[0-9]{8,9}$") String hpNo,
        @NotBlank @Pattern(regexp = "^[0-9]{6}$") String code
) {
}
