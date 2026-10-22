package com.buc.moa.auth.vo.response;

public record SmsResponse(
        boolean success,
        String message
) {
}