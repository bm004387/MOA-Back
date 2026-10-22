package com.buc.moa.notification.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 모바일 앱에서 전달하는 기기 푸시 토큰 요청입니다. */
public record PushTokenRequest(
        @NotBlank @Size(max = 500) String token,
        @NotBlank @Pattern(regexp = "(?i)([0-9]{3}|IPHONE|ANDROID|IPAD|MACBOOK|ETC)") String platform) {
}
