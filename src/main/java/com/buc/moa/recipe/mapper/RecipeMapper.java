package com.buc.moa.recipe.mapper;

import com.buc.moa.recipe.vo.RecipeIngredientVO;
import com.buc.moa.recipe.vo.RecipeCreateRequestVO;
import com.buc.moa.recipe.vo.RecipeIngredientCreateVO;
import com.buc.moa.recipe.vo.RecipeIngredientUnitVO;
import com.buc.moa.recipe.vo.RecipeStepVO;
import com.buc.moa.recipe.vo.RecipeVO;
import java.util.List;

/** 레시피와 레시피 재료·순서 SQL 매퍼입니다. */
public interface RecipeMapper {

    /** 조회 범위와 검색 조건에 맞는 레시피를 반환합니다. */
    List<RecipeVO> selectRecipes(String usrId, String scope, String query, String categoryCode,
            int limit, String postActive, String publicVisibility, String followAccepted);

    /** 사용자가 저장한 레시피 중 현재 조회 가능한 건수를 반환합니다. */
    long countSavedRecipes(String usrId, String postActive, String publicVisibility, String followAccepted);

    /** 레시피 한 건과 사용자의 저장 여부를 반환합니다. */
    RecipeVO selectRecipe(Long postSeq, String usrId, String postActive);

    /** 새 게시물에 사용할 일련번호를 생성합니다. */
    Long nextPostSeq();

    /** 레시피 본문 게시물을 저장합니다. */
    int insertRecipePost(RecipeCreateRequestVO request);

    /** 레시피 분류와 조리 정보를 저장합니다. */
    int insertRecipe(RecipeCreateRequestVO request);

    /** 사용 중인 재료명에 대응하는 재료 마스터 일련번호를 조회합니다. */
    List<Long> selectIngredientSeqs(String ingredientName);

    /** 재료별로 등록된 사용 가능한 계량 단위 연결을 조회합니다. */
    List<RecipeIngredientUnitVO> selectIngredientUnits();

    /** 재료와 선택한 계량 단위의 연결 여부를 확인합니다. */
    int countIngredientUnit(String ingredientGroupCode, String ingredientCode, String unitCode);

    /** 코드 기반 재료와 계량 정보를 저장합니다. */
    int insertRecipeIngredient(RecipeIngredientCreateVO ingredient);

    /** 조리 단계를 저장합니다. */
    int insertRecipeStep(RecipeStepVO step);

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
