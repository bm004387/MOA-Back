package com.buc.moa.recipe.vo;

import com.buc.moa.common.vo.CommonVO;
import lombok.Getter;
import lombok.Setter;

/** 완성 사진을 기존 게시물 파일 관계에 연결할 정보를 담습니다. */
@Getter
@Setter
public class RecipePhotoVO extends CommonVO {

    private Long postSeq;
    private Long filSeq;
    private String mediaType;
}
