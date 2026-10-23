package com.buc.moa.recipe.vo;

import com.buc.moa.common.vo.CommonVO;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

/** 레시피의 조리 순서 정보를 담습니다. */
@Getter
@Setter
public class RecipeStepVO extends CommonVO {

    @JsonIgnore
    private Long postSeq;
    private Integer stepNo;
    private String content;
    private Integer cookSec;
    private Long stepFilSeq;
}
