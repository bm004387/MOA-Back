package com.buc.moa.recipe.vo;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** 레시피에 사용되는 재료 정보를 담습니다. */
@Getter
@Setter
public class RecipeIngredientVO {

    private Integer sortOrd;
    private Long ingrSeq;
    private String ingredientGroupCode;
    private String ingredientCode;
    private String ingrName;
    private BigDecimal ingrAmt;
    private String unitCode;
    private String unitName;
    private String note;
}
