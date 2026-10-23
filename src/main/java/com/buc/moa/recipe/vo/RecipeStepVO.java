package com.buc.moa.recipe.vo;

import lombok.Getter;
import lombok.Setter;

/** 레시피의 조리 순서 정보를 담습니다. */
@Getter
@Setter
public class RecipeStepVO {

    private Integer stepNo;
    private String content;
    private Integer cookSec;
    private Long stepFilSeq;
}
