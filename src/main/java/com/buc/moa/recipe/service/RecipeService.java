package com.buc.moa.recipe.service;

import com.buc.moa.recipe.vo.RecipeVO;
import com.buc.moa.recipe.vo.RecipeCreateRequestVO;
import com.buc.moa.recipe.vo.RecipeListResultVO;
import com.buc.moa.recipe.vo.RecipeIngredientUnitVO;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

/** 레시피 조회와 저장 업무를 정의합니다. */
public interface RecipeService {

    /** 인증된 사용자 기준으로 레시피 목록을 조회합니다. */
    RecipeListResultVO recipes(String authorizationHeader, String scope, String query, String categoryCode,
            int limit);

    /** 인증된 사용자가 열람할 수 있는 레시피를 조회합니다. */
    RecipeVO recipe(String authorizationHeader, Long postSeq);

    /** 로그인 사용자에게 재료별로 선택 가능한 계량 단위 연결을 반환합니다. */
    List<RecipeIngredientUnitVO> ingredientUnits(String authorizationHeader);

    /** 인증된 사용자가 작성한 레시피와 재료·조리 단계를 저장합니다. */
    Long createRecipe(String authorizationHeader, RecipeCreateRequestVO request);

    /** 완성 사진을 포함해 레시피를 등록합니다. */
    Long createRecipeWithPhoto(String authorizationHeader, String requestJson, MultipartFile finishedPhoto);

    /** 로그인 사용자의 레시피 저장 상태를 변경합니다. */
    void setSaved(String authorizationHeader, Long postSeq, boolean enabled);
}
