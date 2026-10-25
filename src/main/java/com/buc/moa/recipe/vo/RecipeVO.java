package com.buc.moa.recipe.vo;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** 레시피 목록과 상세 응답 정보를 담습니다. */
@Getter
@Setter
public class RecipeVO {

    private Long postSeq;
    private String title;
    private String description;
    private String categoryCode;
    private String categoryName;
    private Integer cookingMinutes;
    private Integer servings;
    private String difficultyCode;
    private String difficultyName;
    private String authorId;
    private String authorName;
    private Long profileImageFilSeq;
    private Long finishedPhotoFilSeq;
    private String createdAt;
    private long likeCount;
    private long saveCount;
    private boolean savedByMe;
    private boolean mine;
    private List<RecipeIngredientVO> ingredients = new ArrayList<>();
    private List<RecipeStepVO> steps = new ArrayList<>();
}
