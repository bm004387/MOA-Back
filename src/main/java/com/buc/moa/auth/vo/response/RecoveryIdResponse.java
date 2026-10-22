package com.buc.moa.auth.vo.response;

/** 아이디 찾기 인증 결과입니다. */
public record RecoveryIdResponse(boolean success, String message, String usrId) {
}
