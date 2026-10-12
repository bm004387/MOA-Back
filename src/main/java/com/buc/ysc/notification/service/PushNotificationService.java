package com.buc.ysc.notification.service;

import com.buc.ysc.util.CommonCodeUtil;
import com.buc.ysc.notification.mapper.PushTokenMapper;
import com.buc.ysc.common.util.PushNotificationUtil;
import com.buc.ysc.util.MsgUtil;
import com.buc.ysc.security.SessionManager;
import com.buc.ysc.security.UserSession;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** 기기 푸시 토큰을 저장하고 FCM/APNs 알림을 전송합니다. */
@Service
public class PushNotificationService {

    private final PushTokenMapper mapper;
    private final MsgUtil messages;
    private final PushNotificationUtil pushNotificationUtil;
    private final CommonCodeUtil commonCodeUtil;
    private final SessionManager sessionManager;

    public PushNotificationService(
            PushTokenMapper mapper,
            MsgUtil messages,
            PushNotificationUtil pushNotificationUtil,
            CommonCodeUtil commonCodeUtil,
            SessionManager sessionManager) {
        this.mapper = mapper;
        this.messages = messages;
        this.pushNotificationUtil = pushNotificationUtil;
        this.commonCodeUtil = commonCodeUtil;
        this.sessionManager = sessionManager;
    }

    /** 로그인 사용자의 기기 토큰을 등록합니다. */
    @Transactional
    public void registerToken(String authorizationHeader, String token, String platform) {
        UserSession session = requireSession(authorizationHeader);
        String normalizedPlatform = platform.toUpperCase(Locale.ROOT);
        String detailCode;
        if (normalizedPlatform.matches("[0-9]{3}")) {
            commonCodeUtil.getCodeName("PLATFORM", normalizedPlatform);
            detailCode = normalizedPlatform;
        } else {
            detailCode = commonCodeUtil.getDetailCode("PLATFORM", normalizedPlatform);
        }
        mapper.upsertToken(session.usrId(), token, detailCode);
    }

    /** 로그아웃한 기기의 푸시 토큰을 현재 계정에서 제거합니다. */
    @Transactional
    public void unregisterToken(String authorizationHeader, String token) {
        UserSession session = requireSession(authorizationHeader);
        mapper.deleteToken(session.usrId(), token);
    }

    private UserSession requireSession(String authorizationHeader) {
        UserSession session = authorizationHeader != null && authorizationHeader.startsWith("Bearer ")
                ? sessionManager.getSession(authorizationHeader.substring(7))
                : null;
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        return session;
    }

    /** 팔로우 관계가 저장된 뒤 대상 사용자의 모든 등록 기기로 알림을 보냅니다. */
    public void sendNewFollower(String targetUsrId, String followerUsrId, String followerName) {
        String title = messages.getMsg("COMMUNITY", "053");
        String body = messages.getMsg("COMMUNITY", "054").replace("{0}", followerName);
        pushNotificationUtil.sendToUser(
                targetUsrId,
                title,
                body,
                Map.of("type", "NEW_FOLLOWER", "usrId", followerUsrId));
    }
}
