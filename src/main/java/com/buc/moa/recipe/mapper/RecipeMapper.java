package com.buc.moa.recipe.mapper;

import com.buc.moa.recipe.vo.RecipeIngredientVO;
import com.buc.moa.recipe.vo.RecipeStepVO;
import com.buc.moa.recipe.vo.RecipeVO;
import java.util.List;

/** 레시피와 레시피 재료·순서 SQL 매퍼입니다. */
public interface RecipeMapper {

    /** 조회 범위와 검색 조건에 맞는 레시피를 반환합니다. */
    List<RecipeVO> selectRecipes(String usrId, String scope, String query, String categoryCode,
            int limit, String postActive, String publicVisibility, String followAccepted);

    /** 레시피 한 건과 사용자의 저장 여부를 반환합니다. */
    RecipeVO selectRecipe(Long postSeq, String usrId);

    /** 레시피의 재료 목록을 순서대로 반환합니다. */
    List<RecipeIngredientVO> selectIngredients(Long postSeq);

    /** 레시피의 조리 순서를 반환합니다. */
    List<RecipeStepVO> selectSteps(Long postSeq);

    /** 게시물이 존재하고 로그인 사용자가 열람할 수 있는지 확인합니다. */
    int canReadRecipe(Long postSeq, String usrId, String postActive, String publicVisibility,
            String followAccepted);

    /** 게시물 저장 상태를 추가합니다. */
    int addSave(Long postSeq, String usrId);

    /** 게시물 저장 상태를 제거합니다. */
    int removeSave(Long postSeq, String usrId);
}
