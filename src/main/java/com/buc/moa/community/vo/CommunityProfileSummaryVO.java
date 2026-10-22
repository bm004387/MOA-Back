package com.buc.moa.community.vo;

import com.buc.moa.common.vo.CommonVO;
import lombok.Getter;
import lombok.Setter;

/** 내 게시물 화면에 표시할 게시물·팔로워·팔로잉 수입니다. */
@Getter
@Setter
public class CommunityProfileSummaryVO extends CommonVO {

    private long postCount;
    private long followerCount;
    private long followingCount;
}
