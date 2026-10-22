package com.buc.moa.auth.vo.record;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 아이디와 비밀번호로 로그인하기 위한 요청입니다. */
public record LoginRequest(
        @NotBlank @Size(max = 50) String usrId,
        @NotBlank @Size(max = 72) String pwd
) {
}
