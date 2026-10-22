package com.buc.ysc.auth.vo.record;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 회원가입한 사용자의 PIN을 등록하기 위한 요청입니다. */
public record PinSetupRequest(

        @NotBlank
        @Size(max = 50)
        String usrId,

        @NotBlank
        @Size(max = 72)
        String pwd,

        @NotBlank
        @Pattern(regexp = "^[0-9]{4}$", message = "PIN은 숫자 4자리여야 합니다.")
        String pin
) {
}
