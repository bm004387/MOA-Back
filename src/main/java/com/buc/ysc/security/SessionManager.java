package com.buc.ysc.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

@Component
public class SessionManager {

    // 활동이 없는 세션은 30분 후 만료하고, 활성 세션도 12시간 후에는 재인증합니다.
    private static final String SESSION_EXPIRES_AT_FIELD = "absoluteExpiresAt";

    // Redis Key Prefix
    private static final String SESSION_PREFIX = "auth:session:";
    private static final String USER_SESSIONS_PREFIX = "auth:user-sessions:";

    private final StringRedisTemplate redisTemplate;
    private final Duration sessionIdleTtl;
    private final Duration sessionAbsoluteTtl;

    private final SecureRandom secureRandom = new SecureRandom();

    public SessionManager(
            StringRedisTemplate redisTemplate,
            @Value("${app.security.session-idle-ttl:30m}") Duration sessionIdleTtl,
            @Value("${app.security.session-absolute-ttl:12h}") Duration sessionAbsoluteTtl) {
        this.redisTemplate = redisTemplate;
        this.sessionIdleTtl = sessionIdleTtl;
        this.sessionAbsoluteTtl = sessionAbsoluteTtl;
    }

    /**
     * 로그인 성공 후 Redis에 세션 생성
     *
     * 실제 accessToken 자체는 Redis에 저장하지 않고
     * accessToken의 SHA-256 해시값을 Redis Key로 사용한다.
     */
    public String createSession(UserSession session) {

        // 256bit = 32byte 랜덤 토큰 생성
        byte[] bytes = new byte[32];

        secureRandom.nextBytes(bytes);

        // URL에 사용하기 안전한 문자열로 변환
        String token = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);

        // accessToken을 SHA-256으로 변환하여 Redis Key 생성
        String redisKey = getRedisKey(token);

        long createdAt = System.currentTimeMillis();
        long absoluteExpiresAt = createdAt + sessionAbsoluteTtl.toMillis();

        // Redis에 저장할 세션 정보
        Map<String, String> values = Map.of(
                "usrId", session.usrId(),
                "usrNm", session.usrNm(),
                "rol", session.rol(),
                "hpNo", Objects.toString(session.hpNo(), ""),
                "adr", Objects.toString(session.adr(), ""),
                "dtlAdr", Objects.toString(session.dtlAdr(), ""),
                "createdAt", Long.toString(createdAt),
                SESSION_EXPIRES_AT_FIELD, Long.toString(absoluteExpiresAt)
        );

        // Redis Hash에 세션 저장
        redisTemplate.opsForHash().putAll(redisKey, values);
        String userSessionsKey = USER_SESSIONS_PREFIX + session.usrId();
        redisTemplate.opsForSet().add(userSessionsKey, redisKey);

        redisTemplate.expire(redisKey, sessionIdleTtl);
        redisTemplate.expire(userSessionsKey, sessionAbsoluteTtl);

        return token;
    }

    /**
     * accessToken으로 Redis 세션 조회
     */
    public UserSession getSession(String token) {

        // 토큰이 없으면 인증 실패
        if (token == null || token.isBlank()) {
            return null;
        }

        // accessToken → SHA-256 → Redis Key
        String redisKey = getRedisKey(token);

        // Redis Hash 조회
        Map<Object, Object> values =
                redisTemplate.opsForHash().entries(redisKey);

        // 세션이 없거나 만료된 경우
        if (values.isEmpty()) {
            return null;
        }

        try {

            String expiresAtValue = Objects.toString(
                    values.get(SESSION_EXPIRES_AT_FIELD), "");
            if (expiresAtValue.isBlank()) {
                // 이전 버전에서 발급되어 최대 만료시각이 없는 토큰은 재인증시킵니다.
                deleteSession(token);
                return null;
            }
            long absoluteExpiresAt = Long.parseLong(expiresAtValue);
            if (System.currentTimeMillis() >= absoluteExpiresAt) {
                deleteSession(token);
                return null;
            }

            // Redis에 저장된 사용자 정보로 세션 생성
            UserSession session = new UserSession(
                    values.get("usrId").toString(),
                    values.get("usrNm").toString(),
                    values.get("rol").toString(),
                    Objects.toString(values.get("hpNo"), ""),
                    Objects.toString(values.get("adr"), ""),
                    Objects.toString(values.get("dtlAdr"), "")
            );

            // 유휴 만료는 연장하되, 세션의 절대 만료시각을 넘기지 않습니다.
            refreshTemporarySession(redisKey, absoluteExpiresAt);
            refreshTemporarySessionIndex(session.usrId());

            return session;

        } catch (RuntimeException e) {

            // Redis 데이터가 비정상적인 경우
            // 해당 세션 삭제
            deleteSession(token);

            return null;
        }
    }

    /** 현재 로그인 세션의 표시 이름을 닉네임으로 갱신합니다. */
    public void updateDisplayName(String token, String displayName) {
        if (token == null || token.isBlank()) return;
        redisTemplate.opsForHash().put(getRedisKey(token), "usrNm", displayName);
    }

    /**
     * 로그아웃
     *
     * accessToken에 해당하는 Redis 세션 삭제
     */
    public void deleteSession(String token) {

        if (token == null || token.isBlank()) {
            return;
        }

        String redisKey = getRedisKey(token);

        Object usrId = redisTemplate.opsForHash().get(redisKey, "usrId");
        redisTemplate.delete(redisKey);
        if (usrId != null) {
            redisTemplate.opsForSet().remove(USER_SESSIONS_PREFIX + usrId, redisKey);
        }
    }

    /** 사용자가 보유한 모든 로그인 세션을 로그아웃 처리합니다. */
    public void deleteAllSessionsForUser(String usrId) {
        if (usrId == null || usrId.isBlank()) return;
        String indexKey = USER_SESSIONS_PREFIX + usrId;
        List<String> keysToDelete = findSessionKeysForUser(usrId);
        if (!keysToDelete.isEmpty()) redisTemplate.delete(keysToDelete);
        redisTemplate.delete(indexKey);
    }

    /** 비밀번호 또는 PIN 변경 시 현재 기기 세션만 유지하고 나머지를 폐기합니다. */
    public void deleteOtherSessionsForUser(String usrId, String currentToken) {
        if (usrId == null || usrId.isBlank() || currentToken == null || currentToken.isBlank()) return;

        String indexKey = USER_SESSIONS_PREFIX + usrId;
        String currentSessionKey = getRedisKey(currentToken);
        List<String> sessionKeys = findSessionKeysForUser(usrId);
        List<String> keysToDelete = sessionKeys.stream()
                .filter(sessionKey -> !sessionKey.equals(currentSessionKey))
                .toList();

        if (!keysToDelete.isEmpty()) {
            redisTemplate.delete(keysToDelete);
            redisTemplate.opsForSet().remove(indexKey, keysToDelete.toArray());
        }
    }

    private List<String> findSessionKeysForUser(String usrId) {
        String indexKey = USER_SESSIONS_PREFIX + usrId;
        List<String> matchingKeys = new ArrayList<>();
        var indexedSessionKeys = redisTemplate.opsForSet().members(indexKey);
        if (indexedSessionKeys != null) matchingKeys.addAll(indexedSessionKeys);

        // 인덱스 도입 전 세션도 포함하도록 Redis SCAN으로 보완합니다.
        List<String> allSessionKeys = redisTemplate.execute(
                (org.springframework.data.redis.core.RedisCallback<List<String>>) connection -> {
                    List<String> keys = new ArrayList<>();
                    try (var cursor = connection.scan(
                            org.springframework.data.redis.core.ScanOptions.scanOptions()
                                    .match(SESSION_PREFIX + "*")
                                    .count(500)
                                    .build())) {
                        while (cursor.hasNext()) {
                            keys.add(new String(cursor.next(), StandardCharsets.UTF_8));
                        }
                    }
                    return keys;
                });

        if (allSessionKeys != null) {
            for (String sessionKey : allSessionKeys) {
                Object owner = redisTemplate.opsForHash().get(sessionKey, "usrId");
                if (usrId.equals(owner) && !matchingKeys.contains(sessionKey)) {
                    matchingKeys.add(sessionKey);
                }
            }
        }
        return matchingKeys;
    }

    /** 세션에 저장된 주소 정보를 수정합니다. */
    public void updateAddress(String token, String adr, String dtlAdr) {
        if (token == null || token.isBlank()) return;
        String redisKey = getRedisKey(token);
        redisTemplate.opsForHash().put(redisKey, "adr", Objects.toString(adr, ""));
        redisTemplate.opsForHash().put(redisKey, "dtlAdr", Objects.toString(dtlAdr, ""));
        refreshTemporarySession(redisKey);
    }

    /**
     * accessToken → Redis Key
     *
     * 예:
     *
     * accessToken
     *     ↓
     * SHA-256
     *     ↓
     * auth:session:xxxxxxxx
     */
    private String getRedisKey(String token) {

        return SESSION_PREFIX + sha256(token);
    }

    /**
     * SHA-256 해시 생성
     */
    private String sha256(String value) {

        try {

            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash =
                    digest.digest(
                            value.getBytes(StandardCharsets.UTF_8)
                    );

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException e) {

            throw new IllegalStateException(
                    "SHA-256을 사용할 수 없습니다.",
                    e
            );
        }
    }

    /** 응답에 반환하는 기본 유휴 만료시간(초)입니다. */
    public long getExpiresInSeconds() {
        return sessionIdleTtl.toSeconds();
    }

    private void refreshTemporarySession(String redisKey) {
        Object expiresAtValue = redisTemplate.opsForHash().get(redisKey, SESSION_EXPIRES_AT_FIELD);
        if (expiresAtValue == null) {
            return;
        }
        try {
            refreshTemporarySession(redisKey, Long.parseLong(expiresAtValue.toString()));
        } catch (NumberFormatException ignored) {
            redisTemplate.delete(redisKey);
        }
    }

    private void refreshTemporarySession(String redisKey, long absoluteExpiresAt) {
        long remainingMillis = absoluteExpiresAt - System.currentTimeMillis();
        if (remainingMillis <= 0) {
            redisTemplate.delete(redisKey);
            return;
        }
        long ttlMillis = Math.min(sessionIdleTtl.toMillis(), remainingMillis);
        redisTemplate.expire(redisKey, Duration.ofMillis(ttlMillis));
    }

    private void refreshTemporarySessionIndex(String usrId) {
        String indexKey = USER_SESSIONS_PREFIX + usrId;
        Long ttl = redisTemplate.getExpire(indexKey, TimeUnit.SECONDS);
        if (ttl != null && ttl >= 0) redisTemplate.expire(indexKey, sessionIdleTtl);
    }
}
