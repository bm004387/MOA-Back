package com.buc.moa.common.util;

import com.buc.moa.notification.mapper.PushTokenMapper;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** 여러 업무 서비스가 공통으로 사용하는 FCM 푸시 발송 유틸리티입니다. */
@Component
public class PushNotificationUtil {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationUtil.class);

    private final PushTokenMapper tokenMapper;
    private final ObjectProvider<FirebaseMessaging> firebaseMessagingProvider;

    public PushNotificationUtil(
            PushTokenMapper tokenMapper,
            ObjectProvider<FirebaseMessaging> firebaseMessagingProvider) {
        this.tokenMapper = tokenMapper;
        this.firebaseMessagingProvider = firebaseMessagingProvider;
    }

    /** 사용자에게 등록된 모든 기기로 제목·본문·데이터를 전송합니다. */
    public int sendToUser(
            String usrId,
            String title,
            String body,
            Map<String, String> data) {
        List<String> tokens = tokenMapper.selectActiveTokens(usrId);
        if (tokens == null || tokens.isEmpty()) {
            log.warn("푸시 발송 대상 기기 토큰이 없습니다. 사용자 기기에서 토큰 등록 여부를 확인하세요.");
            return 0;
        }
        int sentCount = 0;
        for (String token : tokens) {
            if (sendToToken(token, title, body, data)) {
                sentCount++;
            }
        }
        return sentCount;
    }

    /** 특정 기기 토큰 하나로 제목·본문·데이터를 전송합니다. */
    public boolean sendToToken(
            String token,
            String title,
            String body,
            Map<String, String> data) {
        FirebaseMessaging messaging = firebaseMessagingProvider.getIfAvailable();
        if (messaging == null) {
            log.info("FCM 발송을 건너뜁니다. APP_PUSH_ENABLED가 활성화되지 않았습니다.");
            return false;
        }

        Message.Builder builder = Message.builder()
                .setToken(token)
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .setApnsConfig(ApnsConfig.builder()
                        .setAps(Aps.builder().setSound("default").build())
                        .build());
        if (data != null) {
            data.forEach(builder::putData);
        }

        try {
            messaging.send(builder.build());
            return true;
        } catch (FirebaseMessagingException e) {
            log.warn("FCM 푸시 전송에 실패했습니다: {}", e.getMessage());
            if (e.getMessagingErrorCode() != null
                    && "UNREGISTERED".equals(e.getMessagingErrorCode().name())) {
                tokenMapper.deleteTokenValue(token);
            }
            return false;
        }
    }
}
