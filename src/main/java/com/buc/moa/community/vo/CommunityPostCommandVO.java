package com.buc.moa.community.vo;

import com.buc.moa.common.vo.CommonVO;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

/** 세션 사용자와 함께 커뮤니티 게시물 변경 정보를 전달하는 VO입니다. */
@Getter
@Setter
public class CommunityPostCommandVO extends CommonVO {

    private Long postSeq;
    private Long cmtSeq;
    private String usrId;
    private String content;
    private String commentContent;
    private String visibility;
    private Boolean enabled;
    private Long parentCmtSeq;
    private List<MultipartFile> files;
}
