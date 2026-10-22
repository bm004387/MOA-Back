package com.buc.moa.notification.service;

import com.buc.moa.notification.NewFollowerEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 팔로우 DB 트랜잭션 커밋 후 FCM 알림을 전송합니다. */
@Component
public class NewFollowerPushListener {

    private final PushNotificationService pushNotificationService;

    public NewFollowerPushListener(PushNotificationService pushNotificationService) {
        this.pushNotificationService = pushNotificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNewFollower(NewFollowerEvent event) {
        pushNotificationService.sendNewFollower(event.targetUsrId(), event.followerUsrId(), event.followerName());
    }
}
