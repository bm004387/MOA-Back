package com.buc.moa.user.vo.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 닉네임 변경 요청입니다. */
public record NicknameChangeRequest(
        @NotBlank
        @Size(max = 100)
        String nckNm
) {
}
