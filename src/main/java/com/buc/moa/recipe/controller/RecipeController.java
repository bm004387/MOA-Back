package com.buc.moa.recipe.controller;

import com.buc.moa.recipe.service.RecipeService;
import com.buc.moa.recipe.vo.RecipeCreateRequestVO;
import com.buc.moa.recipe.vo.RecipeListResultVO;
import com.buc.moa.recipe.vo.RecipeSaveRequestVO;
import com.buc.moa.recipe.vo.RecipeIngredientUnitVO;
import com.buc.moa.recipe.vo.RecipeVO;
import org.springframework.http.MediaType;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 레시피 목록·상세·작성·수정·삭제와 반응 API를 제공합니다. */
@RestController
@RequestMapping("/api/recipes")
public class RecipeController {

    private final RecipeService service;

    public RecipeController(RecipeService service) {
        this.service = service;
    }

    /** 홈·내 레시피·저장 목록과 검색 결과를 조회합니다. */
    @GetMapping
    public ResponseEntity<List<RecipeVO>> recipes(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestParam(defaultValue = "all") String scope, @RequestParam(defaultValue = "") String query,
            @RequestParam(required = false) String categoryCode, @RequestParam(defaultValue = "50") int limit) {
        RecipeListResultVO result = service.recipes(authorizationHeader, scope, query, categoryCode, limit);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (result.savedCount() != null) {
            response.header("X-Saved-Count", String.valueOf(result.savedCount()));
        }
        return response.body(result.recipes());
    }

    /** 재료별로 선택 가능한 계량 단위 연결 목록을 조회합니다. */
    @GetMapping("/ingredient-units")
    public List<RecipeIngredientUnitVO> ingredientUnits(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        return service.ingredientUnits(authorizationHeader);
    }

    /** 레시피 상세와 재료·조리 순서를 조회합니다. */
    @GetMapping("/{postSeq}")
    public RecipeVO recipe(@RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable Long postSeq) {
        return service.recipe(authorizationHeader, postSeq);
    }

    /** 레시피와 재료·조리 단계를 새 게시물로 등록합니다. */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Long> createRecipe(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestBody RecipeCreateRequestVO request) {
        return Map.of("postSeq", service.createRecipe(authorizationHeader, request));
    }

    /** 선택한 완성 사진과 레시피 내용을 함께 등록합니다. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Long> createRecipeWithPhoto(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestParam("request") String requestJson,
            @RequestPart(value = "finishedPhoto", required = false) MultipartFile finishedPhoto) {
        return Map.of("postSeq", service.createRecipeWithPhoto(authorizationHeader, requestJson, finishedPhoto));
    }

    /** 작성자 본인의 레시피 본문·재료·조리 단계를 수정합니다. */
    @PutMapping(value = "/{postSeq}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Boolean> updateRecipe(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable Long postSeq, @RequestBody RecipeCreateRequestVO request) {
        service.updateRecipe(authorizationHeader, postSeq, request);
        return Map.of("success", true);
    }

    /** 새 완성 사진을 선택한 경우 사진과 레시피 본문을 함께 수정합니다. */
    @PutMapping(value = "/{postSeq}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Boolean> updateRecipeWithPhoto(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable Long postSeq, @RequestParam("request") String requestJson,
            @RequestPart(value = "finishedPhoto", required = false) MultipartFile finishedPhoto) {
        service.updateRecipeWithPhoto(authorizationHeader, postSeq, requestJson, finishedPhoto);
        return Map.of("success", true);
    }

    /** 작성자 본인의 레시피를 삭제 상태로 변경합니다. */
    @DeleteMapping("/{postSeq}")
    public Map<String, Boolean> deleteRecipe(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable Long postSeq) {
        service.deleteRecipe(authorizationHeader, postSeq);
        return Map.of("success", true);
    }

    /** 로그인 사용자의 레시피 좋아요 상태를 변경합니다. */
    @PutMapping("/{postSeq}/like")
    public Map<String, Boolean> setLiked(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable Long postSeq, @RequestBody RecipeSaveRequestVO request) {
        service.setLiked(authorizationHeader, postSeq, request.isEnabled());
        return Map.of("success", true);
    }

    /** 레시피를 저장하거나 저장 취소합니다. */
    @PutMapping("/{postSeq}/save")
    public Map<String, Boolean> setSaved(@RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable Long postSeq, @RequestBody RecipeSaveRequestVO request) {
        service.setSaved(authorizationHeader, postSeq, request.isEnabled());
        return Map.of("success", true);
    }
}
