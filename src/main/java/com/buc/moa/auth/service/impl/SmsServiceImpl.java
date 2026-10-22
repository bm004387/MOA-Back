package com.buc.moa.auth.service.impl;

import com.buc.moa.auth.service.SmsService;
import com.buc.moa.auth.vo.request.SmsSendRequest;
import com.buc.moa.auth.vo.request.SmsVerifyRequest;
import com.buc.moa.auth.vo.response.SmsResponse;
import com.buc.moa.user.mapper.UserMapper;
import com.buc.moa.util.MsgUtil;
import com.solapi.sdk.message.model.Message;
import com.solapi.sdk.message.service.DefaultMessageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

@Service
public class SmsServiceImpl implements SmsService {

    private static final String SMS_KEY_PREFIX = "sms:verify:";
    private static final String SMS_ATTEMPT_PREFIX = "sms:verify-attempt:";
    private static final String SMS_VERIFIED_PREFIX = "sms:signup-verified:";
    private static final String RECOVERY_KEY_PREFIX = "sms:recovery:";
    private static final Duration CODE_TTL = Duration.ofMinutes(3);
    private static final Duration RECOVERY_VERIFICATION_TTL = Duration.ofMinutes(5);
    private static final Duration SEND_MINUTE_TTL = Duration.ofMinutes(1);
    private static final Duration SEND_DAY_TTL = Duration.ofDays(1);
    private static final int MAX_SENDS_PER_MINUTE = 1;
    private static final int MAX_SENDS_PER_DAY = 5;
    private static final int MAX_VERIFY_ATTEMPTS = 5;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final DefaultRedisScript<Long> SEND_QUOTA_SCRIPT = new DefaultRedisScript<>(
            "local minuteCount = tonumber(redis.call('GET', KEYS[1]) or '0'); "
                    + "local dayCount = tonumber(redis.call('GET', KEYS[2]) or '0'); "
                    + "if minuteCount >= tonumber(ARGV[1]) or dayCount >= tonumber(ARGV[3]) then return 0; end; "
                    + "local minuteUpdated = redis.call('INCR', KEYS[1]); "
                    + "if minuteUpdated == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[2]); end; "
                    + "local dayUpdated = redis.call('INCR', KEYS[2]); "
                    + "if dayUpdated == 1 then redis.call('PEXPIRE', KEYS[2], ARGV[4]); end; "
                    + "return 1",
            Long.class);
    private static final DefaultRedisScript<Long> VERIFY_CODE_SCRIPT = new DefaultRedisScript<>(
            "local saved = redis.call('GET', KEYS[1]); "
                    + "if not saved then return -1; end; "
                    + "if saved == ARGV[1] then "
                    + "redis.call('DEL', KEYS[1]); redis.call('DEL', KEYS[2]); "
                    + "redis.call('SET', KEYS[3], 'Y', 'PX', ARGV[2]); return 1; end; "
                    + "local attempts = redis.call('INCR', KEYS[2]); "
                    + "if attempts == 1 then redis.call('PEXPIRE', KEYS[2], ARGV[3]); end; "
                    + "if attempts >= tonumber(ARGV[4]) then redis.call('DEL', KEYS[1]); return 0; end; "
                    + "return -2",
            Long.class);
    private static final DefaultRedisScript<Long> CONSUME_VERIFICATION_SCRIPT = new DefaultRedisScript<>(
            "local value = redis.call('GET', KEYS[1]); "
                    + "if value == ARGV[1] then redis.call('DEL', KEYS[1]); return 1; end; "
                    + "return 0",
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final DefaultMessageService solapiMessageService;
    private final MsgUtil msgUtil;
    private final UserMapper userMapper;

    @Value("${solapi.sender}")
    private String sender;

    public SmsServiceImpl(StringRedisTemplate redisTemplate, DefaultMessageService solapiMessageService, MsgUtil msgUtil, UserMapper userMapper) {
        this.redisTemplate = redisTemplate;
        this.solapiMessageService = solapiMessageService;
        this.msgUtil = msgUtil;
        this.userMapper = userMapper;
    }

    @Override
    public SmsResponse send(SmsSendRequest request) {

        String hpNo = normalizePhone(request.hpNo());
        String key = smsCodeKey(hpNo);
        String verifiedKey = signupVerifiedKey(hpNo);

        if (userMapper.existsHpNo(hpNo)) {
            return new SmsResponse(false, msgUtil.getMsg("SIGNUP", "005"));
        }

        reserveSendQuota(hpNo);
        redisTemplate.delete(verifiedKey);

        String code = generateCode();

        try {
            // Redis에 인증번호 저장
            redisTemplate.opsForValue().set(key, code, CODE_TTL);
            redisTemplate.delete(verificationAttemptKey(key));

            // SMS 발송
            Message message = new Message();
            message.setFrom(sender);
            message.setTo(hpNo);
            message.setText("[한끼모아] 인증번호는 " + code + "입니다.");

            solapiMessageService.send(message, null);

            return new SmsResponse(true,  msgUtil.getMsg("SMS", "001"));

        } catch (Exception e) {

            // 문자 발송 실패 시 Redis에 저장한 인증번호 삭제
            redisTemplate.delete(key);
            return new SmsResponse(false, msgUtil.getMsg("SMS", "005"));
        }
    }

    @Override
    public SmsResponse verify(SmsVerifyRequest request) {

        String hpNo = normalizePhone(request.hpNo());
        Long result = verifyCode(
                smsCodeKey(hpNo),
                verificationAttemptKey(smsCodeKey(hpNo)),
                signupVerifiedKey(hpNo),
                request.code());
        if (result == null || result == -1L) {
            return new SmsResponse(false, msgUtil.getMsg("SMS", "002"));
        }
        if (result != 1L) {
            return new SmsResponse(false, msgUtil.getMsg("SMS", "003"));
        }
        return new SmsResponse(true, msgUtil.getMsg("SMS", "004"));
    }

    /** 인증에 성공한 전화번호 증명을 회원가입에서 한 번만 사용합니다. */
    @Override
    public boolean consumeSignupVerification(String hpNo) {
        return consumeVerification(signupVerifiedKey(normalizePhone(hpNo)));
    }

    @Override
    public SmsResponse sendIdRecovery(String phone) {
        String hpNo = normalizePhone(phone);
        if (!userMapper.existsHpNo(hpNo)) {
            return new SmsResponse(false, msgUtil.getMsg("AUTH", "009"));
        }
        return sendRecoveryCode("ID", hpNo, hpNo);
    }

    @Override
    public SmsResponse verifyIdRecovery(String phone, String code) {
        String hpNo = normalizePhone(phone);
        return verifyRecoveryCode("ID", hpNo, hpNo, code);
    }

    @Override
    public SmsResponse sendPasswordRecovery(String usrId, String phone) {
        String hpNo = normalizePhone(phone);
        var user = userMapper.selectByUsrId(usrId);
        if (user == null || !normalizePhone(user.getHpNo()).equals(hpNo)) {
            return new SmsResponse(false, msgUtil.getMsg("AUTH", "010"));
        }
        return sendRecoveryCode("PASSWORD:" + usrId, hpNo, usrId + ":" + hpNo);
    }

    @Override
    public SmsResponse verifyPasswordRecovery(String usrId, String phone, String code) {
        String hpNo = normalizePhone(phone);
        var user = userMapper.selectByUsrId(usrId);
        if (user == null || !normalizePhone(user.getHpNo()).equals(hpNo)) {
            return new SmsResponse(false, msgUtil.getMsg("AUTH", "010"));
        }
        return verifyRecoveryCode("PASSWORD:" + usrId, hpNo, usrId + ":" + hpNo, code);
    }

    @Override
    public boolean consumeIdRecoveryVerification(String phone) {
        return consumeVerification("ID", normalizePhone(phone));
    }

    @Override
    public boolean consumePasswordRecoveryVerification(String usrId, String phone) {
        return consumeVerification("PASSWORD:" + usrId,
                usrId + ":" + normalizePhone(phone));
    }

    private SmsResponse sendRecoveryCode(String purpose, String hpNo, String subject) {
        String codeKey = recoveryCodeKey(purpose, subject);
        String verifiedKey = recoveryVerifiedKey(purpose, subject);
        reserveSendQuota(hpNo);
        redisTemplate.delete(List.of(codeKey, verifiedKey, verificationAttemptKey(codeKey)));
        String code = generateCode();
        try {
            redisTemplate.opsForValue().set(codeKey, code, CODE_TTL);
            Message message = new Message();
            message.setFrom(sender);
            message.setTo(hpNo);
            message.setText("[한끼모아] 인증번호는 " + code + "입니다.");
            solapiMessageService.send(message, null);
            return new SmsResponse(true, msgUtil.getMsg("SMS", "001"));
        } catch (Exception e) {
            redisTemplate.delete(codeKey);
            return new SmsResponse(false, msgUtil.getMsg("SMS", "005"));
        }
    }

    private SmsResponse verifyRecoveryCode(String purpose, String hpNo, String subject, String code) {
        String codeKey = recoveryCodeKey(purpose, subject);
        Long result = verifyCode(
                codeKey,
                verificationAttemptKey(codeKey),
                recoveryVerifiedKey(purpose, subject),
                code);
        if (result == null || result == -1L) {
            return new SmsResponse(false, msgUtil.getMsg("SMS", "002"));
        }
        if (result != 1L) {
            return new SmsResponse(false, msgUtil.getMsg("SMS", "003"));
        }
        return new SmsResponse(true, msgUtil.getMsg("SMS", "004"));
    }

    private boolean consumeVerification(String purpose, String subject) {
        return consumeVerification(recoveryVerifiedKey(purpose, subject));
    }

    private boolean consumeVerification(String key) {
        Long consumed = redisTemplate.execute(CONSUME_VERIFICATION_SCRIPT, List.of(key), "Y");
        return consumed != null && consumed == 1L;
    }

    private String recoveryCodeKey(String purpose, String subject) {
        return RECOVERY_KEY_PREFIX + purpose + ":code:" + digest(subject);
    }

    private String recoveryVerifiedKey(String purpose, String subject) {
        return RECOVERY_KEY_PREFIX + purpose + ":verified:" + digest(subject);
    }

    private String smsCodeKey(String hpNo) {
        return SMS_KEY_PREFIX + digest(hpNo);
    }

    private String signupVerifiedKey(String hpNo) {
        return SMS_VERIFIED_PREFIX + digest(hpNo);
    }

    private String verificationAttemptKey(String codeKey) {
        return SMS_ATTEMPT_PREFIX + digest(codeKey);
    }

    private Long verifyCode(String codeKey, String attemptKey, String verifiedKey, String code) {
        return redisTemplate.execute(
                VERIFY_CODE_SCRIPT,
                List.of(codeKey, attemptKey, verifiedKey),
                code,
                Long.toString(RECOVERY_VERIFICATION_TTL.toMillis()),
                Long.toString(CODE_TTL.toMillis()),
                Integer.toString(MAX_VERIFY_ATTEMPTS));
    }

    private void reserveSendQuota(String hpNo) {
        String phoneHash = digest(hpNo);
        Long allowed = redisTemplate.execute(
                SEND_QUOTA_SCRIPT,
                List.of("sms:send-limit:minute:" + phoneHash, "sms:send-limit:day:" + phoneHash),
                Integer.toString(MAX_SENDS_PER_MINUTE),
                Long.toString(SEND_MINUTE_TTL.toMillis()),
                Integer.toString(MAX_SENDS_PER_DAY),
                Long.toString(SEND_DAY_TTL.toMillis()));
        if (allowed == null || allowed != 1L) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    msgUtil.getMsg("SMS", "007"));
        }
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

    private String generateCode() {
        return String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
    }

    private String normalizePhone(String hpNo) {
        return hpNo == null ? "" : hpNo.replaceAll("[^0-9]", "");
    }
}
