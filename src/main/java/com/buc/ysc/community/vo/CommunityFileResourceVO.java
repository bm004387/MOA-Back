package com.buc.ysc.community.vo;

/** 접근이 확인된 커뮤니티 첨부 파일의 전송 정보를 담습니다. */
public record CommunityFileResourceVO(String path, String contentType) {
}
