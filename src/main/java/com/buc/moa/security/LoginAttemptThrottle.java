package com.buc.moa.security;

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
    private static final String PIN_KEY_PREFIX = "auth:pin-login-fail:";
    private static final String TRY_ATTEMPT_SCRIPT = "local ipCount = tonumber(redis.call('GET', KEYS[1]) or '0'); "
            + "local accountCount = tonumber(redis.call('GET', KEYS[2]) or '0'); "
            + "if ipCount >= tonumber(ARGV[1]) or accountCount >= tonumber(ARGV[2]) then return 0; end; "
            + "local nextIpCount = redis.call('INCR', KEYS[1]); "
            + "if nextIpCount == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[3]); end; "
            + "local nextAccountCount = redis.call('INCR', KEYS[2]); "
            + "if nextAccountCount == 1 then redis.call('PEXPIRE', KEYS[2], ARGV[3]); end; "
            + "return 1";
    private static final DefaultRedisScript<Long> TRY_ATTEMPT =
            new DefaultRedisScript<>(TRY_ATTEMPT_SCRIPT, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final int maxFailuresPerAccountAndIp;
    private final int maxFailuresPerAccount;
    private final Duration window;
    private final int maxPinFailuresPerAccountAndIp;
    private final int maxPinFailuresPerAccount;
    private final Duration pinWindow;

    public LoginAttemptThrottle(StringRedisTemplate redisTemplate,
            @Value("${app.security.login.max-failures-per-account-ip:10}") int maxFailuresPerAccountAndIp,
            @Value("${app.security.login.max-failures-per-account:30}") int maxFailuresPerAccount,
            @Value("${app.security.login.failure-window:15m}") Duration window,
            @Value("${app.security.pin.max-failures-per-account-ip:5}") int maxPinFailuresPerAccountAndIp,
            @Value("${app.security.pin.max-failures-per-account:5}") int maxPinFailuresPerAccount,
            @Value("${app.security.pin.failure-window:15m}") Duration pinWindow) {
        this.redisTemplate = redisTemplate;
        this.maxFailuresPerAccountAndIp = maxFailuresPerAccountAndIp;
        this.maxFailuresPerAccount = maxFailuresPerAccount;
        this.window = window;
        this.maxPinFailuresPerAccountAndIp = maxPinFailuresPerAccountAndIp;
        this.maxPinFailuresPerAccount = maxPinFailuresPerAccount;
        this.pinWindow = pinWindow;
    }

    public boolean tryPasswordAttempt(String usrId, String remoteAddress) {
        String normalizedUserId = normalizeUserId(usrId);
        String normalizedAddress = normalizeAddress(remoteAddress);
        return tryAttempt(accountAddressKey(normalizedUserId, normalizedAddress),
                accountKey(normalizedUserId), maxFailuresPerAccountAndIp,
                maxFailuresPerAccount, window);
    }

    public void clearAccountFailures(String usrId, String remoteAddress) {
        String normalizedUserId = normalizeUserId(usrId);
        String normalizedAddress = normalizeAddress(remoteAddress);
        redisTemplate.delete(List.of(accountAddressKey(normalizedUserId, normalizedAddress),
                accountKey(normalizedUserId)));
    }

    /** PIN 인증은 짧은 숫자 코드이므로 별도 기준으로 더 엄격하게 제한합니다. */
    public boolean tryPinAttempt(String usrId, String remoteAddress) {
        String normalizedUserId = normalizeUserId(usrId);
        String normalizedAddress = normalizeAddress(remoteAddress);
        return tryAttempt(pinAccountAddressKey(normalizedUserId, normalizedAddress),
                pinAccountKey(normalizedUserId), maxPinFailuresPerAccountAndIp,
                maxPinFailuresPerAccount, pinWindow);
    }

    public void clearPinFailures(String usrId, String remoteAddress) {
        String normalizedUserId = normalizeUserId(usrId);
        String normalizedAddress = normalizeAddress(remoteAddress);
        redisTemplate.delete(List.of(
                pinAccountAddressKey(normalizedUserId, normalizedAddress),
                pinAccountKey(normalizedUserId)));
    }

    private boolean tryAttempt(String accountAddressKey, String accountKey,
            int maxFailuresPerAccountAndIp, int maxFailuresPerAccount, Duration ttl) {
        Long allowed = redisTemplate.execute(TRY_ATTEMPT, List.of(accountAddressKey, accountKey),
                Integer.toString(maxFailuresPerAccountAndIp),
                Integer.toString(maxFailuresPerAccount),
                Long.toString(ttl.toMillis()));
        return allowed != null && allowed == 1L;
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

    private String pinAccountAddressKey(String usrId, String remoteAddress) {
        return PIN_KEY_PREFIX + "user-ip:" + digest(usrId + ":" + remoteAddress);
    }

    private String pinAccountKey(String usrId) {
        return PIN_KEY_PREFIX + "user:" + digest(usrId);
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
