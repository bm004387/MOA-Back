package com.buc.moa.community.vo;

import com.buc.moa.common.vo.CommonVO;
import lombok.Getter;
import lombok.Setter;

/** 게시물 목록에 표시할 최신 댓글 미리보기입니다. */
@Getter
@Setter
public class CommunityCommentPreviewVO extends CommonVO {

    private Long postSeq;
    private Long cmtSeq;
    private Long parentCmtSeq;
    private String usrId;
    private String usrNm;
    private Long profileImageFilSeq;
    private String cmtCn;
    private String cmtDtm;
}
