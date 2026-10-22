package com.buc.moa.user.vo.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 내 PIN 번호를 변경하는 요청입니다. */
public record PinChangeRequest(
        @NotBlank @Pattern(regexp = "^[0-9]{4}$") String pin
) {
}
