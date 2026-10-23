package com.buc.moa.recipe.vo;

import com.buc.moa.common.vo.CommonVO;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** 레시피 등록 시 선택한 재료·계량 코드를 담습니다. */
@Getter
@Setter
public class RecipeIngredientCreateVO extends CommonVO {

    private Long postSeq;
    private Integer sortOrd;
    private String ingredientGroupCode;
    private String ingredientCode;
    private String ingredientName;
    private String amount;
    private BigDecimal ingrAmt;
    private String unitCode;
    private String note;
}
