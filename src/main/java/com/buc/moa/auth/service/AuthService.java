package com.buc.moa.auth.service;


import com.buc.moa.auth.vo.record.UsrIdCheckResponse;
import com.buc.moa.auth.vo.response.LoginResponse;
import com.buc.moa.auth.vo.response.SignupResponse;
import com.buc.moa.auth.vo.record.SignupRequest;
import com.buc.moa.auth.vo.record.PinLoginRequest;
import com.buc.moa.auth.vo.record.PinSetupRequest;
import com.buc.moa.auth.vo.record.LoginRequest;
import com.buc.moa.auth.vo.record.RecoveryIdRequest;
import com.buc.moa.auth.vo.record.RecoveryPasswordSendRequest;
import com.buc.moa.auth.vo.record.RecoveryPasswordVerifyRequest;
import com.buc.moa.auth.vo.record.PasswordResetRequest;
import com.buc.moa.auth.vo.response.SmsResponse;
import com.buc.moa.auth.vo.response.RecoveryIdResponse;
import com.buc.moa.auth.vo.response.UserInfoResponse;

public interface AuthService {

    SignupResponse signup(SignupRequest request);

    void setupPin(PinSetupRequest request, String remoteAddress);

    LoginResponse loginWithPin(PinLoginRequest request, String remoteAddress);

    LoginResponse login(LoginRequest request, String remoteAddress);

    UsrIdCheckResponse checkUsrId(String usrId);

    SmsResponse sendIdRecoveryCode(String hpNo);

    RecoveryIdResponse findId(RecoveryIdRequest request);

    SmsResponse sendPasswordRecoveryCode(RecoveryPasswordSendRequest request);

    SmsResponse verifyPasswordRecoveryCode(RecoveryPasswordVerifyRequest request);

    SmsResponse resetPassword(PasswordResetRequest request);

    String logout(String authorizationHeader);

    UserInfoResponse validateSession(String authorizationHeader);
}
