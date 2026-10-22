package com.buc.moa.notification;

/** 승인된 신규 팔로우가 커밋된 뒤 푸시 알림을 전송하기 위한 이벤트입니다. */
public record NewFollowerEvent(String targetUsrId, String followerUsrId, String followerName) {
}
