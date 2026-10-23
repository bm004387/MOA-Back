package com.buc.moa.recipe.vo;

import com.buc.moa.common.vo.CommonVO;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** 레시피 등록 요청과 게시물 저장에 필요한 정보를 담습니다. */
@Getter
@Setter
public class RecipeCreateRequestVO extends CommonVO {

    private Long postSeq;
    private String usrId;
    private String title;
    private String description;
    private String category;
    private String categoryCode;
    private Integer cookingMinutes;
    private Integer servings;
    private String difficultyCode;
    private String postStat;
    private String visibility;
    private String steps;
    private List<RecipeIngredientCreateVO> ingredients = new ArrayList<>();
}
