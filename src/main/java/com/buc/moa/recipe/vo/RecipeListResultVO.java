package com.buc.moa.recipe.vo;

import java.util.List;

/** 레시피 목록과 선택적으로 함께 전달할 저장 개수입니다. */
public record RecipeListResultVO(List<RecipeVO> recipes, Long savedCount) {
}
