package com.buc.moa.auth.vo.response;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserInfoResponse user
) {
}