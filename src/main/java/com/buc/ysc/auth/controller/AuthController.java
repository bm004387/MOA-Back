package com.buc.ysc.auth.controller;

import com.buc.ysc.auth.service.AuthService;
import com.buc.ysc.auth.vo.record.UsrIdCheckResponse;
import com.buc.ysc.auth.vo.response.LoginResponse;
import com.buc.ysc.auth.vo.response.SignupResponse;
import com.buc.ysc.auth.vo.response.UserInfoResponse;
import com.buc.ysc.auth.vo.record.SignupRequest;
import com.buc.ysc.auth.vo.record.PinLoginRequest;
import com.buc.ysc.auth.vo.record.PinSetupRequest;
import com.buc.ysc.auth.vo.record.LoginRequest;
import com.buc.ysc.auth.vo.record.RecoveryIdRequest;
import com.buc.ysc.auth.vo.record.RecoveryIdSendRequest;
import com.buc.ysc.auth.vo.record.RecoveryPasswordSendRequest;
import com.buc.ysc.auth.vo.record.RecoveryPasswordVerifyRequest;
import com.buc.ysc.auth.vo.record.PasswordResetRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 회원가입
     */
    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.signup(request));
    }

    @GetMapping("/check-user-id")
    public ResponseEntity<UsrIdCheckResponse> checkUsrId(@RequestParam String usrId) {
        return ResponseEntity.ok(authService.checkUsrId(usrId.trim()));
    }

    /** PIN 설정이 없거나 로그아웃한 사용자를 아이디와 비밀번호로 로그인합니다. */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.login(request, httpRequest.getRemoteAddr()));
    }

    /** 아이디 찾기에 사용할 휴대폰 인증번호를 발송합니다. */
    @PostMapping("/recovery/id/send")
    public ResponseEntity<?> sendIdRecoveryCode(@Valid @RequestBody RecoveryIdSendRequest request) {
        return ResponseEntity.ok(authService.sendIdRecoveryCode(request.hpNo()));
    }

    /** 휴대폰 인증번호를 확인하고 아이디를 찾습니다. */
    @PostMapping("/recovery/id/find")
    public ResponseEntity<?> findId(@Valid @RequestBody RecoveryIdRequest request) {
        return ResponseEntity.ok(authService.findId(request));
    }

    /** 아이디와 휴대폰 번호 확인 후 비밀번호 재설정 코드를 발송합니다. */
    @PostMapping("/recovery/password/send")
    public ResponseEntity<?> sendPasswordRecoveryCode(@Valid @RequestBody RecoveryPasswordSendRequest request) {
        return ResponseEntity.ok(authService.sendPasswordRecoveryCode(request));
    }

    /** 비밀번호 재설정용 휴대폰 인증번호를 확인합니다. */
    @PostMapping("/recovery/password/verify")
    public ResponseEntity<?> verifyPasswordRecoveryCode(@Valid @RequestBody RecoveryPasswordVerifyRequest request) {
        return ResponseEntity.ok(authService.verifyPasswordRecoveryCode(request));
    }

    /** 인증이 완료된 계정의 비밀번호를 새 비밀번호로 변경합니다. */
    @PostMapping("/recovery/password/reset")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        return ResponseEntity.ok(authService.resetPassword(request));
    }

    /** 가입 시 전달한 비밀번호를 확인한 뒤 PIN 해시를 저장합니다. */
    @PostMapping("/pin/setup")
    public ResponseEntity<Void> setupPin(
            @Valid @RequestBody PinSetupRequest request,
            HttpServletRequest httpRequest) {
        authService.setupPin(request, httpRequest.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    /** PIN을 검증하고 Redis 로그인 세션을 발급합니다. */
    @PostMapping("/pin/login")
    public ResponseEntity<LoginResponse> loginWithPin(
            @Valid @RequestBody PinLoginRequest request,
            HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(authService.loginWithPin(request, httpRequest.getRemoteAddr()));
    }

    /** 저장된 access token으로 로그인 세션을 확인합니다. */
    @GetMapping("/session")
    public ResponseEntity<UserInfoResponse> validateSession(HttpServletRequest request) {
        UserInfoResponse userInfo = authService.validateSession(request.getHeader("Authorization"));
        if (userInfo == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(userInfo);
    }

    /**
     * 로그아웃
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        return ResponseEntity.ok(java.util.Map.of(
                "message", authService.logout(request.getHeader("Authorization"))));
    }
}
