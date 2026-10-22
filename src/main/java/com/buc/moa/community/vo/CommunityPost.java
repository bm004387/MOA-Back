package com.buc.moa.community.vo;

import java.util.List;

/** 피드에 표시할 게시물 및 반응 요약 정보입니다. */
public record CommunityPost(
        Long postSeq,
        String authorId,
        String authorName,
        Long profileImageFilSeq,
        String content,
        String createdAt,
        long likeCount,
        long commentCount,
        boolean likedByMe,
        boolean savedByMe,
        List<CommunityMedia> media
) {

    /** 게시물에 첨부된 미디어의 응답 정보입니다. */
    public record CommunityMedia(
            Long filSeq,
            String mediaType,
            String contentType
    ) {}

    /** 게시물 댓글 응답 정보입니다. */
    public record CommunityComment(
            Long cmtSeq,
            Long parentCmtSeq,
            String usrId,
            String usrNm,
            Long profileImageFilSeq,
            String cmtCn,
            String cmtDtm,
            boolean deleted
    ) {}
}
