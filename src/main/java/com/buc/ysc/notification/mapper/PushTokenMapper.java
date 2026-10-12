package com.buc.ysc.notification.mapper;

import java.util.List;

/** 사용자 기기 푸시 토큰 데이터 접근 인터페이스입니다. */
public interface PushTokenMapper {

    /** 기기 토큰을 등록하거나 기존 소유자와 플랫폼 정보를 갱신합니다. */
    int upsertToken(String usrId, String token, String platform);

    /** 로그인한 사용자의 기기 토큰 하나를 비활성화합니다. */
    int deleteToken(String usrId, String token);

    /** 사용자의 활성 기기 토큰을 조회합니다. */
    List<String> selectActiveTokens(String usrId);

    /** 발송 불가능한 토큰을 삭제합니다. */
    int deleteTokenValue(String token);
}
