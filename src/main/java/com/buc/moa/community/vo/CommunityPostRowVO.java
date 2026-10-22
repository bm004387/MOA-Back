package com.buc.moa.community.vo;

import com.buc.moa.common.vo.CommonVO;
import lombok.Getter;
import lombok.Setter;

/** 게시물 조회 SQL의 기본 컬럼을 담는 MyBatis 매핑용 VO입니다. */
@Getter
@Setter
public class CommunityPostRowVO extends CommonVO {

    private Long postSeq;
    private String authorId;
    private String authorName;
    private Long profileImageFilSeq;
    private String content;
    private String createdAt;
    private long likeCount;
    private long commentCount;
    private boolean likedByMe;
    private boolean savedByMe;
}
