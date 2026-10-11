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

@Component
public class SessionManager {

    // 로그인 세션 유지시간 30분
    private static final Duration SESSION_TTL = Duration.ofMinutes(30);

    // Redis Key Prefix
    private static final String SESSION_PREFIX = "auth:session:";
    private static final String USER_SESSIONS_PREFIX = "auth:user-sessions:";

    private final StringRedisTemplate redisTemplate;

    private final SecureRandom secureRandom = new SecureRandom();

    public SessionManager(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 로그인 성공 후 Redis에 세션 생성
     *
     * 실제 accessToken 자체는 Redis에 저장하지 않고
     * accessToken의 SHA-256 해시값을 Redis Key로 사용한다.
     */
    public String createSession(UserSession session, boolean rememberMe) {

        // 256bit = 32byte 랜덤 토큰 생성
        byte[] bytes = new byte[32];

        secureRandom.nextBytes(bytes);

        // URL에 사용하기 안전한 문자열로 변환
        String token = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);

        // accessToken을 SHA-256으로 변환하여 Redis Key 생성
        String redisKey = getRedisKey(token);

        // Redis에 저장할 세션 정보
        Map<String, String> values = Map.of(
                "usrId", session.usrId(),
                "usrNm", session.usrNm(),
                "rol", session.rol(),
                "hpNo", Objects.toString(session.hpNo(), ""),
                "adr", Objects.toString(session.adr(), ""),
                "dtlAdr", Objects.toString(session.dtlAdr(), "")
        );

        // Redis Hash에 세션 저장
        redisTemplate.opsForHash().putAll(redisKey, values);
        String userSessionsKey = USER_SESSIONS_PREFIX + session.usrId();
        redisTemplate.opsForSet().add(userSessionsKey, redisKey);

        // 자동 로그인 선택 시 로그아웃 전까지 세션을 유지하고, 아니면 30분 유휴 만료를 적용합니다.
        if (!rememberMe) {
            redisTemplate.expire(redisKey, SESSION_TTL);
            redisTemplate.expire(userSessionsKey, SESSION_TTL);
        }

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

            // Redis에 저장된 사용자 정보로 세션 생성
            UserSession session = new UserSession(
                    values.get("usrId").toString(),
                    values.get("usrNm").toString(),
                    values.get("rol").toString(),
                    Objects.toString(values.get("hpNo"), ""),
                    Objects.toString(values.get("adr"), ""),
                    Objects.toString(values.get("dtlAdr"), "")
            );

            // 만료 시간이 설정된 세션만 슬라이딩 연장합니다. TTL -1은 자동 로그인 세션입니다.
            refreshTemporarySession(redisKey);
            refreshTemporarySessionIndex(session.usrId());

            return session;

        } catch (RuntimeException e) {

            // Redis 데이터가 비정상적인 경우
            // 해당 세션 삭제
            redisTemplate.delete(redisKey);

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
        var sessionKeys = redisTemplate.opsForSet().members(indexKey);
        List<String> keysToDelete = new ArrayList<>();
        if (sessionKeys != null) keysToDelete.addAll(sessionKeys);

        // 인덱스 도입 전 생성된 세션도 포함하도록 Redis SCAN으로 기존 세션을 확인합니다.
        List<String> legacySessionKeys = redisTemplate.execute(
                (org.springframework.data.redis.core.RedisCallback<List<String>>) connection -> {
            List<String> matchingKeys = new ArrayList<>();
            try (var cursor = connection.scan(org.springframework.data.redis.core.ScanOptions.scanOptions()
                    .match(SESSION_PREFIX + "*").count(500).build())) {
                while (cursor.hasNext()) {
                    matchingKeys.add(new String(cursor.next(), StandardCharsets.UTF_8));
                }
            }
            return matchingKeys;
        });
        if (legacySessionKeys != null) {
            for (String sessionKey : legacySessionKeys) {
                Object owner = redisTemplate.opsForHash().get(sessionKey, "usrId");
                if (usrId.equals(owner)) keysToDelete.add(sessionKey);
            }
        }
        if (!keysToDelete.isEmpty()) redisTemplate.delete(keysToDelete);
        redisTemplate.delete(indexKey);
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

    /** 자동 로그인 세션은 0(만료 없음), 일반 세션은 30분을 반환합니다. */
    public long getExpiresInSeconds(boolean rememberMe) {
        return rememberMe ? 0 : SESSION_TTL.toSeconds();
    }

    private void refreshTemporarySession(String redisKey) {
        Long ttl = redisTemplate.getExpire(redisKey, TimeUnit.SECONDS);
        if (ttl != null && ttl >= 0) {
            redisTemplate.expire(redisKey, SESSION_TTL);
        }
    }

    private void refreshTemporarySessionIndex(String usrId) {
        String indexKey = USER_SESSIONS_PREFIX + usrId;
        Long ttl = redisTemplate.getExpire(indexKey, TimeUnit.SECONDS);
        if (ttl != null && ttl >= 0) redisTemplate.expire(indexKey, SESSION_TTL);
    }
}
