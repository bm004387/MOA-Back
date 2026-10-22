package com.buc.moa.auth.vo.record;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 아이디와 PIN으로 로그인하기 위한 요청입니다. */
public record PinLoginRequest(

        @NotBlank
        @Size(max = 50)
        String usrId,

        @NotBlank
        @Pattern(regexp = "^[0-9]{4}$", message = "PIN은 숫자 4자리여야 합니다.")
        String pin,

        @Pattern(regexp = "^([A-Za-z0-9_-]{43})?$", message = "세션 정보 형식이 올바르지 않습니다.")
        String sessionId
) {
}
