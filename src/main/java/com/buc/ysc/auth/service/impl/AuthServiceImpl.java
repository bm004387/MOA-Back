package com.buc.ysc.auth.service.impl;

import com.buc.ysc.auth.vo.response.SmsResponse;
import com.buc.ysc.security.SessionManager;
import com.buc.ysc.security.UserSession;
import com.buc.ysc.security.LoginAttemptThrottle;
import com.buc.ysc.user.mapper.UserMapper;
import com.buc.ysc.auth.service.AuthService;
import com.buc.ysc.auth.vo.record.UsrIdCheckResponse;
import com.buc.ysc.auth.vo.response.LoginResponse;
import com.buc.ysc.auth.vo.response.SignupResponse;
import com.buc.ysc.auth.vo.response.UserInfoResponse;
import com.buc.ysc.auth.vo.record.PinLoginRequest;
import com.buc.ysc.auth.vo.record.PinSetupRequest;
import com.buc.ysc.auth.vo.record.SignupRequest;
import com.buc.ysc.auth.vo.record.LoginRequest;
import com.buc.ysc.auth.vo.record.RecoveryIdRequest;
import com.buc.ysc.auth.vo.record.RecoveryPasswordSendRequest;
import com.buc.ysc.auth.vo.record.RecoveryPasswordVerifyRequest;
import com.buc.ysc.auth.vo.record.PasswordResetRequest;
import com.buc.ysc.auth.vo.response.RecoveryIdResponse;
import com.buc.ysc.auth.service.SmsService;
import com.buc.ysc.user.vo.request.UserVO;
import com.buc.ysc.util.MsgUtil;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final SessionManager sessionManager;
    private final MsgUtil msgUtil;
    private final LoginAttemptThrottle loginAttemptThrottle;
    private final SmsService smsService;


    public AuthServiceImpl(UserMapper userMapper, PasswordEncoder passwordEncoder,
            SessionManager sessionManager, MsgUtil msgUtil,
            LoginAttemptThrottle loginAttemptThrottle, SmsService smsService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.sessionManager = sessionManager;
        this.msgUtil = msgUtil;
        this.loginAttemptThrottle = loginAttemptThrottle;
        this.smsService = smsService;
    }

    /** 로그인 세션을 제거하고 로그아웃 메시지를 반환합니다. */
    @Override
    public String logout(String authorizationHeader) {
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            String token = authorizationHeader.substring(7);
            UserSession session = sessionManager.getSession(token);
            if (session != null) {
                sessionManager.deleteSession(token);
                sessionManager.deleteAllSessionsForUser(session.usrId());
            } else {
                sessionManager.deleteSession(token);
            }
        }
        return msgUtil.getMsg("AUTH", "002");
    }

    /** Authorization 헤더의 Redis 세션을 조회해 사용자 정보를 반환합니다. */
    @Override
    public UserInfoResponse validateSession(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return null;
        }
        UserSession session = sessionManager.getSession(authorizationHeader.substring(7));
        if (session == null) {
            return null;
        }
        return new UserInfoResponse(session.usrId(), session.usrNm(), session.rol(),
                session.hpNo(), session.adr(), session.dtlAdr());
    }


    /**
     * 회원가입
     */
    @Override
    public SignupResponse signup(SignupRequest request) {

        // 아이디 중복 확인
        if (userMapper.existsByUsrId(request.usrId()) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, msgUtil.getMsg("AUTH","002"));
        }

        UserVO user = new UserVO();
        user.setSystemUserId(request.usrId());
        user.setUsrId(request.usrId());
        // 입력받은 비밀번호를 Bcrypt로 암호화
        user.setPwd(passwordEncoder.encode(request.pwd()));
        user.setUsrNm(request.nckNm());
        user.setNckNm(request.nckNm());
        user.setRol("001");
        user.setHpNo(request.hpNo());
        user.setAdr(request.adr());
        user.setDtlAdr(request.dtlAdr());
        userMapper.insertUser(user);


        return new SignupResponse(msgUtil.getMsg("AUTH", "005"), request.usrId());
    }

    @Override
    public UsrIdCheckResponse checkUsrId(String usrId) {
        int count = userMapper.existsByUsrId(usrId);

        if (count > 0) {
            return new UsrIdCheckResponse(false, msgUtil.getMsg("AUTH", "002"));
        }
        return new UsrIdCheckResponse(true, msgUtil.getMsg("AUTH", "003"));
    }

    /** 가입 시 입력한 원래 비밀번호를 확인하고 PIN 해시를 저장합니다. */
    @Override
    public void setupPin(PinSetupRequest request, String remoteAddress) {
        if (loginAttemptThrottle.isBlocked(request.usrId(), remoteAddress)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    msgUtil.getMsg("AUTH", "006"));
        }

        UserVO user = userMapper.selectByUsrId(request.usrId());
        if (user == null || !passwordEncoder.matches(request.pwd(), user.getPwd())) {
            loginAttemptThrottle.recordFailure(request.usrId(), remoteAddress);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, msgUtil.getMsg("AUTH", "001"));
        }

        user.setSystemUserId(user.getUsrId());
        user.setPinPwd(passwordEncoder.encode(request.pin()));
        if (userMapper.updatePinPassword(user) != 1) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    msgUtil.getMsg("SIGNUP", "004"));
        }
        loginAttemptThrottle.clearAccountFailures(request.usrId(), remoteAddress);
    }

    /** 로그아웃 뒤 사용할 아이디·비밀번호 로그인을 처리합니다. */
    @Override
    public LoginResponse login(LoginRequest request, String remoteAddress) {
        if (loginAttemptThrottle.isBlocked(request.usrId(), remoteAddress)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    msgUtil.getMsg("AUTH", "006"));
        }

        UserVO user = userMapper.selectByUsrId(request.usrId());
        if (user == null || !passwordEncoder.matches(request.pwd(), user.getPwd())) {
            loginAttemptThrottle.recordFailure(request.usrId(), remoteAddress);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, msgUtil.getMsg("AUTH", "001"));
        }

        loginAttemptThrottle.clearAccountFailures(request.usrId(), remoteAddress);

        UserSession session = new UserSession(user.getUsrId(), displayName(user), user.getRol(),
                user.getHpNo(), user.getAdr(), user.getDtlAdr());
        String accessToken = sessionManager.createSession(session);
        UserInfoResponse userInfo = new UserInfoResponse(user.getUsrId(), displayName(user), user.getRol(),
                user.getHpNo(), user.getAdr(), user.getDtlAdr());
        return new LoginResponse(accessToken, "Bearer", sessionManager.getExpiresInSeconds(),
                userInfo);
    }

    /** BCrypt PIN을 검증하고 기존 Redis 세션이 있으면 재사용합니다. */
    @Override
    public LoginResponse loginWithPin(PinLoginRequest request, String remoteAddress) {
        if (loginAttemptThrottle.isBlocked(request.usrId(), remoteAddress)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    msgUtil.getMsg("AUTH", "006"));
        }

        UserVO user = userMapper.selectByUsrId(request.usrId());
        if (user == null) {
            loginAttemptThrottle.recordFailure(request.usrId(), remoteAddress);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, msgUtil.getMsg("AUTH", "008"));
        }
        if (user.getPinPwd() == null || !passwordEncoder.matches(request.pin(), user.getPinPwd())) {
            loginAttemptThrottle.recordFailure(request.usrId(), remoteAddress);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, msgUtil.getMsg("AUTH", "008"));
        }
        loginAttemptThrottle.clearAccountFailures(request.usrId(), remoteAddress);

        UserSession session = new UserSession(user.getUsrId(), displayName(user), user.getRol(),
                user.getHpNo(), user.getAdr(), user.getDtlAdr());
        UserSession existingSession = sessionManager.getSession(request.sessionId());
        String accessToken;
        if (existingSession != null && existingSession.usrId().equals(user.getUsrId())) {
            accessToken = request.sessionId();
        } else {
            sessionManager.deleteSession(request.sessionId());
            accessToken = sessionManager.createSession(session);
        }
        UserInfoResponse userInfo = new UserInfoResponse(user.getUsrId(), displayName(user),
                user.getRol(), user.getHpNo(), user.getAdr(), user.getDtlAdr());
        return new LoginResponse(accessToken, "Bearer", sessionManager.getExpiresInSeconds(),
                userInfo);
    }

    /** 등록된 휴대폰 번호로 아이디 찾기 인증번호를 보냅니다. */
    @Override
    public SmsResponse sendIdRecoveryCode(String hpNo) {
        return smsService.sendIdRecovery(hpNo);
    }

    /** 인증번호를 확인하고 휴대폰 번호에 등록된 아이디를 반환합니다. */
    @Override
    public RecoveryIdResponse findId(RecoveryIdRequest request) {
        SmsResponse verification = smsService.verifyIdRecovery(request.hpNo(), request.code());
        if (!verification.success()) {
            return new RecoveryIdResponse(false, verification.message(), null);
        }
        smsService.consumeIdRecoveryVerification(request.hpNo());
        String usrId = userMapper.selectUsrIdByHpNo(normalizePhone(request.hpNo()));
        if (usrId == null) {
            return new RecoveryIdResponse(false, msgUtil.getMsg("AUTH", "009"), null);
        }
        return new RecoveryIdResponse(true, verification.message(), usrId);
    }

    /** 아이디와 휴대폰 번호가 등록 정보와 일치할 때
     * 비밀번호 재설정 코드를 보냅니다. */
    @Override
    public SmsResponse sendPasswordRecoveryCode(RecoveryPasswordSendRequest request) {
        return smsService.sendPasswordRecovery(request.usrId(), request.hpNo());
    }

    /** 비밀번호 재설정 인증번호를 확인하고 서버에 짧은 인증 상태를 저장합니다. */
    @Override
    public SmsResponse verifyPasswordRecoveryCode(RecoveryPasswordVerifyRequest request) {
        return smsService.verifyPasswordRecovery(request.usrId(), request.hpNo(), request.code());
    }

    /** 휴대폰 인증이 완료된 계정의 비밀번호를 변경합니다. */
    @Override
    public SmsResponse resetPassword(PasswordResetRequest request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            return new SmsResponse(false, msgUtil.getMsg("COMMON", "003"));
        }
        if (!smsService.consumePasswordRecoveryVerification(request.usrId(), request.hpNo())) {
            return new SmsResponse(false, msgUtil.getMsg("SMS", "006"));
        }
        UserVO update = new UserVO();
        update.setUsrId(request.usrId());
        update.setPwd(passwordEncoder.encode(request.newPassword()));
        update.setSystemUserId(request.usrId());
        if (userMapper.updatePassword(update) != 1) {
            return new SmsResponse(false, msgUtil.getMsg("MYINFO", "008"));
        }
        sessionManager.deleteAllSessionsForUser(request.usrId());
        return new SmsResponse(true, msgUtil.getMsg("AUTH", "011"));
    }

    private String normalizePhone(String hpNo) {
        return hpNo.replaceAll("[^0-9]", "");
    }

    private String displayName(UserVO user) {
        return user.getNckNm() == null || user.getNckNm().isBlank()
                ? user.getUsrNm()
                : user.getNckNm();
    }
}
