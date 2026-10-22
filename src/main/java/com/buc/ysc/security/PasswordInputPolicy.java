package com.buc.ysc.security;

import java.nio.charset.StandardCharsets;

/** BCrypt의 72바이트 입력 한계를 초과하는 비밀번호를 거부합니다. */
public final class PasswordInputPolicy {

    private static final int MAX_BCRYPT_PASSWORD_BYTES = 72;

    private PasswordInputPolicy() {
    }

    public static boolean isWithinBcryptLimit(String password) {
        return password != null
                && password.getBytes(StandardCharsets.UTF_8).length <= MAX_BCRYPT_PASSWORD_BYTES;
    }
}
