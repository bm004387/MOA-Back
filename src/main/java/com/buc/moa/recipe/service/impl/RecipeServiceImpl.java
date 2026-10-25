package com.buc.moa.recipe.service.impl;

import com.buc.moa.recipe.mapper.RecipeMapper;
import com.buc.moa.recipe.service.RecipeService;
import com.buc.moa.recipe.vo.RecipeCreateRequestVO;
import com.buc.moa.recipe.vo.RecipeIngredientCreateVO;
import com.buc.moa.recipe.vo.RecipeListResultVO;
import com.buc.moa.recipe.vo.RecipeStepVO;
import com.buc.moa.recipe.vo.RecipeVO;
import com.buc.moa.security.SessionManager;
import com.buc.moa.security.UserSession;
import com.buc.moa.util.CommonCodeUtil;
import com.buc.moa.util.MsgUtil;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 레시피 조회와 저장 업무 규칙을 구현합니다. */
@Service
public class RecipeServiceImpl implements RecipeService {

    private static final int MAX_LIMIT = 100;
    private final RecipeMapper mapper;
    private final SessionManager sessionManager;
    private final CommonCodeUtil commonCodeUtil;
    private final MsgUtil msgUtil;

    public RecipeServiceImpl(RecipeMapper mapper, SessionManager sessionManager,
            CommonCodeUtil commonCodeUtil, MsgUtil msgUtil) {
        this.mapper = mapper;
        this.sessionManager = sessionManager;
        this.commonCodeUtil = commonCodeUtil;
        this.msgUtil = msgUtil;
    }

    /** Bearer 토큰을 검증하고 지정한 범위의 레시피를 조회합니다. */
    @Override
    public RecipeListResultVO recipes(String authorizationHeader, String scope, String query, String categoryCode,
            int limit) {
        UserSession session = requireSession(authorizationHeader);
        String normalizedScope = scope == null ? "all" : scope.trim().toLowerCase();
        if (!List.of("all", "mine", "saved").contains(normalizedScope)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msgUtil.getMsg("RECIPE", "001"));
        }
        int safeLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
        List<RecipeVO> recipes = mapper.selectRecipes(session.usrId(), normalizedScope,
                query == null ? "" : query.trim(),
                categoryCode == null || categoryCode.isBlank() ? null : categoryCode.trim(), safeLimit,
                code("POST_STAT", "ACTIVE"), code("VIS_TYP", "PUBLIC"), code("FOLLOW_STAT", "ACCEPTED"));
        Long savedCount = "mine".equals(normalizedScope)
                ? mapper.countSavedRecipes(session.usrId(), code("POST_STAT", "ACTIVE"),
                        code("VIS_TYP", "PUBLIC"), code("FOLLOW_STAT", "ACCEPTED"))
                : null;
        return new RecipeListResultVO(recipes, savedCount);
    }

    /** 공개 범위와 게시 상태를 확인한 뒤 재료와 조리 순서를 포함해 반환합니다. */
    @Override
    public RecipeVO recipe(String authorizationHeader, Long postSeq) {
        UserSession session = requireSession(authorizationHeader);
        if (mapper.canReadRecipe(postSeq, session.usrId(), code("POST_STAT", "ACTIVE"),
                code("VIS_TYP", "PUBLIC"), code("FOLLOW_STAT", "ACCEPTED")) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, msgUtil.getMsg("RECIPE", "002"));
        }
        RecipeVO recipe = mapper.selectRecipe(postSeq, session.usrId(), code("POST_STAT", "ACTIVE"));
        if (recipe == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, msgUtil.getMsg("RECIPE", "002"));
        }
        recipe.setIngredients(mapper.selectIngredients(postSeq));
        recipe.setSteps(mapper.selectSteps(postSeq));
        return recipe;
    }

    /** 레시피·재료·조리 단계를 한 트랜잭션으로 저장하고 게시물 일련번호를 반환합니다. */
    @Override
    @Transactional
    public Long createRecipe(String authorizationHeader, RecipeCreateRequestVO request) {
        UserSession session = requireSession(authorizationHeader);
        if (request == null || request.getTitle() == null || request.getTitle().isBlank()) {
            throw badRequest("003");
        }

        request.setTitle(request.getTitle().trim());
        request.setDescription(request.getDescription() == null ? "" : request.getDescription().trim());
        request.setUsrId(session.usrId());
        request.setSystemUserId(session.usrId());
        request.setPostSeq(mapper.nextPostSeq());
        request.setPostStat(code("POST_STAT", "ACTIVE"));
        request.setVisibility(code("VIS_TYP", "PUBLIC"));
        request.setCategoryCode(resolveCategoryCode(request.getCategoryCode(), request.getCategory()));
        request.setCookingMinutes(request.getCookingMinutes() == null ? 20 : request.getCookingMinutes());
        request.setServings(request.getServings() == null ? 2 : request.getServings());
        request.setDifficultyCode(resolveDifficultyCode(request.getDifficultyCode()));

        if (request.getCookingMinutes() < 1 || request.getServings() < 1) {
            throw badRequest("005");
        }

        List<RecipeIngredientCreateVO> ingredients = request.getIngredients();
        if (ingredients == null || ingredients.isEmpty()) {
            throw badRequest("006");
        }
        prepareIngredients(ingredients, request.getPostSeq(), session.usrId());

        List<RecipeStepVO> steps = parseSteps(request.getSteps(), request.getPostSeq(), session.usrId());
        if (steps.isEmpty()) {
            throw badRequest("012");
        }

        requireInserted(mapper.insertRecipePost(request), "레시피 게시물을 저장하지 못했습니다.");
        requireInserted(mapper.insertRecipe(request), "레시피 정보를 저장하지 못했습니다.");
        for (RecipeIngredientCreateVO ingredient : ingredients) {
            requireInserted(mapper.insertRecipeIngredient(ingredient), "레시피 재료를 저장하지 못했습니다.");
        }
        for (RecipeStepVO step : steps) {
            requireInserted(mapper.insertRecipeStep(step), "레시피 조리 순서를 저장하지 못했습니다.");
        }
        return request.getPostSeq();
    }

    /** 레시피 접근 권한을 검증하고 저장 상태를 반영합니다. */
    @Override
    @Transactional
    public void setSaved(String authorizationHeader, Long postSeq, boolean enabled) {
        UserSession session = requireSession(authorizationHeader);
        if (mapper.canReadRecipe(postSeq, session.usrId(), code("POST_STAT", "ACTIVE"),
                code("VIS_TYP", "PUBLIC"), code("FOLLOW_STAT", "ACCEPTED")) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, msgUtil.getMsg("RECIPE", "002"));
        }
        if (enabled) {
            mapper.addSave(postSeq, session.usrId());
        } else {
            mapper.removeSave(postSeq, session.usrId());
        }
    }

    /** 요청의 Bearer 토큰에서 유효한 로그인 세션을 반환합니다. */
    private UserSession requireSession(String authorizationHeader) {
        UserSession session = authorizationHeader != null && authorizationHeader.startsWith("Bearer ")
                ? sessionManager.getSession(authorizationHeader.substring(7))
                : null;
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, msgUtil.getMsg("MYINFO", "009"));
        }
        return session;
    }

    /** 업무 상태값을 공통코드 상세코드로 변환합니다. */
    private String code(String group, String value) {
        return commonCodeUtil.getDetailCode(group, value);
    }

    /** 분류코드 또는 화면에 표시된 분류명을 등록된 레시피 분류 코드로 변환합니다. */
    private String resolveCategoryCode(String categoryCode, String categoryName) {
        try {
            if (categoryCode != null && !categoryCode.isBlank()) {
                commonCodeUtil.getCodeDesc("RCP_CTG", categoryCode.trim());
                return categoryCode.trim();
            }
            if (categoryName != null && !categoryName.isBlank()) {
                return commonCodeUtil.getAllCodes().stream()
                        .filter(item -> "RCP_CTG".equalsIgnoreCase(item.getComCd()))
                        .filter(item -> matches(item.getComDtlDesc(), categoryName)
                                || matches(item.getComDtlNm(), categoryName)
                                || matches(item.getComDtlCd(), categoryName))
                        .map(item -> item.getComDtlCd())
                        .findFirst()
                        .orElseThrow(() -> badRequest("004"));
            }
        } catch (IllegalArgumentException exception) {
            throw badRequest("004");
        }
        throw badRequest("004");
    }

    /** 선택된 난이도 코드를 확인하고 미선택 시 등록된 첫 난이도를 기본값으로 사용합니다. */
    private String resolveDifficultyCode(String difficultyCode) {
        if (difficultyCode != null && !difficultyCode.isBlank()) {
            try {
                commonCodeUtil.getCodeDesc("RCP_DIF", difficultyCode.trim());
                return difficultyCode.trim();
            } catch (IllegalArgumentException exception) {
                throw badRequest("004");
            }
        }
        return commonCodeUtil.getAllCodes().stream()
                .filter(item -> "RCP_DIF".equalsIgnoreCase(item.getComCd()))
                .map(item -> item.getComDtlCd())
                .findFirst()
                .orElse(null);
    }

    /** 재료·분류·계량 코드를 검증하고 DB 저장에 필요한 공통 값을 설정합니다. */
    private void prepareIngredients(List<RecipeIngredientCreateVO> ingredients, Long postSeq, String usrId) {
        int sortOrd = 1;
        for (RecipeIngredientCreateVO ingredient : ingredients) {
            if (ingredient == null || ingredient.getIngredientGroupCode() == null
                    || ingredient.getIngredientCode() == null) {
                throw badRequest("007");
            }
            String groupCode = ingredient.getIngredientGroupCode().trim().toUpperCase();
            if (!isIngredientGroup(groupCode)) {
                throw badRequest("007");
            }
            try {
                ingredient.setIngredientName(commonCodeUtil.getCodeDesc(groupCode,
                        ingredient.getIngredientCode().trim()));
                if (ingredient.getUnitCode() != null && !ingredient.getUnitCode().isBlank()) {
                    commonCodeUtil.getCodeDesc("RCP_UNIT", ingredient.getUnitCode().trim());
                }
            } catch (IllegalArgumentException exception) {
                throw badRequest("008");
            }

            List<Long> ingredientSeqs = mapper.selectIngredientSeqs(ingredient.getIngredientName());
            if (ingredientSeqs.size() != 1) {
                throw badRequest("008");
            }
            ingredient.setIngrSeq(ingredientSeqs.get(0));

            String amount = ingredient.getAmount() == null ? "" : ingredient.getAmount().trim();
            if (!amount.isEmpty()) {
                try {
                    BigDecimal parsedAmount = new BigDecimal(amount);
                    if (parsedAmount.signum() <= 0) {
                        throw badRequest("009");
                    }
                    ingredient.setIngrAmt(parsedAmount);
                } catch (NumberFormatException exception) {
                    throw badRequest("010");
                }
                if (ingredient.getUnitCode() == null || ingredient.getUnitCode().isBlank()) {
                    throw badRequest("011");
                }
            } else {
                ingredient.setIngrAmt(null);
            }

            ingredient.setIngredientGroupCode(groupCode);
            ingredient.setIngredientCode(ingredient.getIngredientCode().trim());
            ingredient.setUnitCode(blankToNull(ingredient.getUnitCode()));
            ingredient.setPostSeq(postSeq);
            ingredient.setSortOrd(sortOrd++);
            ingredient.setSystemUserId(usrId);
        }
    }

    /** 줄바꿈으로 전달된 조리 단계를 순서가 있는 VO 목록으로 변환합니다. */
    private List<RecipeStepVO> parseSteps(String stepText, Long postSeq, String usrId) {
        List<RecipeStepVO> steps = new ArrayList<>();
        if (stepText == null || stepText.isBlank()) {
            return steps;
        }
        for (String line : stepText.split("\\R")) {
            String content = line.trim();
            if (content.isEmpty()) {
                continue;
            }
            RecipeStepVO step = new RecipeStepVO();
            step.setPostSeq(postSeq);
            step.setStepNo(steps.size() + 1);
            step.setContent(content);
            step.setSystemUserId(usrId);
            steps.add(step);
        }
        return steps;
    }

    private boolean isIngredientGroup(String groupCode) {
        return "RCP_INGR".equals(groupCode)
                || "INGR".equals(groupCode)
                || groupCode.startsWith("RCP_INGR_")
                || groupCode.startsWith("INGR_");
    }

    private boolean matches(String value, String candidate) {
        return value != null && value.trim().equalsIgnoreCase(candidate.trim());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ResponseStatusException badRequest(String messageCode) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msgUtil.getMsg("RECIPE", messageCode));
    }

    private void requireInserted(int affectedRows, String message) {
        if (affectedRows != 1) {
            throw new IllegalStateException(message);
        }
    }
}
