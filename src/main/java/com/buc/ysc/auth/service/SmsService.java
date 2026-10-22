package com.buc.ysc.auth.service;

import com.buc.ysc.auth.vo.request.SmsSendRequest;
import com.buc.ysc.auth.vo.request.SmsVerifyRequest;
import com.buc.ysc.auth.vo.response.SmsResponse;

public interface SmsService {

    SmsResponse send(SmsSendRequest request);

    SmsResponse verify(SmsVerifyRequest request);

    boolean consumeSignupVerification(String hpNo);

    SmsResponse sendIdRecovery(String hpNo);

    SmsResponse verifyIdRecovery(String hpNo, String code);

    SmsResponse sendPasswordRecovery(String usrId, String hpNo);

    SmsResponse verifyPasswordRecovery(String usrId, String hpNo, String code);

    boolean consumeIdRecoveryVerification(String hpNo);

    boolean consumePasswordRecoveryVerification(String usrId, String hpNo);
}
