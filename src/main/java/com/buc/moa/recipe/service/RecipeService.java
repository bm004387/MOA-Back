package com.buc.moa.recipe.service;

import com.buc.moa.recipe.vo.RecipeVO;
import java.util.List;

/** 레시피 조회와 저장 업무를 정의합니다. */
public interface RecipeService {

    /** 인증된 사용자 기준으로 레시피 목록을 조회합니다. */
    List<RecipeVO> recipes(String authorizationHeader, String scope, String query, String categoryCode,
            int limit);

    /** 인증된 사용자가 열람할 수 있는 레시피를 조회합니다. */
    RecipeVO recipe(String authorizationHeader, Long postSeq);

    /** 로그인 사용자의 레시피 저장 상태를 변경합니다. */
    void setSaved(String authorizationHeader, Long postSeq, boolean enabled);
}
