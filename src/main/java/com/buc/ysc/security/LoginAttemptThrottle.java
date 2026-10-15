package com.buc.ysc.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

/** 비밀번호·PIN 인증 실패를 계정과 계정/IP 조합별로 Redis에서 제한합니다. */
@Component
public class LoginAttemptThrottle {

    private static final String KEY_PREFIX = "auth:login-fail:";
    private static final String INCREMENT_SCRIPT = "local count = redis.call('INCR', KEYS[1]); "
            + "if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]); end; "
            + "return count";
    private static final DefaultRedisScript<Long> INCREMENT_WITH_TTL =
            new DefaultRedisScript<>(INCREMENT_SCRIPT, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final int maxFailuresPerAccountAndIp;
    private final int maxFailuresPerAccount;
    private final Duration window;

    public LoginAttemptThrottle(StringRedisTemplate redisTemplate,
            @Value("${app.security.login.max-failures-per-account-ip:10}") int maxFailuresPerAccountAndIp,
            @Value("${app.security.login.max-failures-per-account:30}") int maxFailuresPerAccount,
            @Value("${app.security.login.failure-window:15m}") Duration window) {
        this.redisTemplate = redisTemplate;
        this.maxFailuresPerAccountAndIp = maxFailuresPerAccountAndIp;
        this.maxFailuresPerAccount = maxFailuresPerAccount;
        this.window = window;
    }

    public boolean isBlocked(String usrId, String remoteAddress) {
        String normalizedUserId = normalizeUserId(usrId);
        String normalizedAddress = normalizeAddress(remoteAddress);
        return count(accountAddressKey(normalizedUserId, normalizedAddress))
                        >= maxFailuresPerAccountAndIp
                || count(accountKey(normalizedUserId)) >= maxFailuresPerAccount;
    }

    public void recordFailure(String usrId, String remoteAddress) {
        String normalizedUserId = normalizeUserId(usrId);
        String normalizedAddress = normalizeAddress(remoteAddress);
        increment(accountAddressKey(normalizedUserId, normalizedAddress));
        increment(accountKey(normalizedUserId));
    }

    public void clearAccountFailures(String usrId, String remoteAddress) {
        String normalizedUserId = normalizeUserId(usrId);
        String normalizedAddress = normalizeAddress(remoteAddress);
        redisTemplate.delete(List.of(accountAddressKey(normalizedUserId, normalizedAddress),
                accountKey(normalizedUserId)));
    }

    private long count(String key) {
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return 0;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            redisTemplate.delete(key);
            return 0;
        }
    }

    private void increment(String key) {
        redisTemplate.execute(INCREMENT_WITH_TTL, List.of(key), Long.toString(window.toMillis()));
    }

    private String normalizeUserId(String usrId) {
        return usrId == null ? "" : usrId.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeAddress(String remoteAddress) {
        return remoteAddress == null || remoteAddress.isBlank()
                ? "unknown"
                : remoteAddress.trim();
    }

    private String accountAddressKey(String usrId, String remoteAddress) {
        return KEY_PREFIX + "user-ip:" + digest(usrId + ":" + remoteAddress);
    }

    private String accountKey(String usrId) {
        return KEY_PREFIX + "user:" + digest(usrId);
    }

    private String digest(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }
}
