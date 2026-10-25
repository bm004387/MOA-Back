package com.buc.moa.recipe.vo;

import lombok.Getter;
import lombok.Setter;

/** 재료 공통코드와 허용 계량 단위의 연결 정보를 담습니다. */
@Getter
@Setter
public class RecipeIngredientUnitVO {

    private String ingredientGroupCode;
    private String ingredientCode;
    private String unitCode;
    private Integer sortOrd;
}
