package com.buc.moa.auth.vo.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SmsVerifyRequest(

        @NotBlank(message = "휴대폰 번호를 입력해주세요.")
        @Pattern(
                regexp = "^01[0-9]{8,9}$",
                message = "올바른 휴대폰 번호를 입력해주세요."
        )
        String hpNo,

        @NotBlank(message = "인증번호를 입력해주세요.")
        @Pattern(
                regexp = "^[0-9]{6}$",
                message = "인증번호는 6자리 숫자입니다."
        )
        String code

) {
}