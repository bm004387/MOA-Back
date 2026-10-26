package com.buc.moa.recipe.service.impl;

import com.buc.moa.common.code.vo.CommonCodeVO;
import com.buc.moa.file.service.FileStorageService;
import com.buc.moa.recipe.mapper.RecipeMapper;
import com.buc.moa.recipe.service.RecipeService;
import com.buc.moa.recipe.vo.RecipeCreateRequestVO;
import com.buc.moa.recipe.vo.RecipeIngredientCreateVO;
import com.buc.moa.recipe.vo.RecipeIngredientVO;
import com.buc.moa.recipe.vo.RecipeIngredientUnitVO;
import com.buc.moa.recipe.vo.RecipeListResultVO;
import com.buc.moa.recipe.vo.RecipePhotoVO;
import com.buc.moa.recipe.vo.RecipeStepVO;
import com.buc.moa.recipe.vo.RecipeVO;
import com.buc.moa.security.SessionManager;
import com.buc.moa.security.UserSession;
import com.buc.moa.util.CommonCodeUtil;
import com.buc.moa.util.MsgUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** 레시피 조회와 저장 업무 규칙을 구현합니다. */
@Service
public class RecipeServiceImpl implements RecipeService {

    private static final int MAX_LIMIT = 100;
    private final RecipeMapper mapper;
    private final SessionManager sessionManager;
    private final CommonCodeUtil commonCodeUtil;
    private final MsgUtil msgUtil;
    private final FileStorageService fileStorage;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RecipeServiceImpl(RecipeMapper mapper, SessionManager sessionManager,
            CommonCodeUtil commonCodeUtil, MsgUtil msgUtil, FileStorageService fileStorage) {
        this.mapper = mapper;
        this.sessionManager = sessionManager;
        this.commonCodeUtil = commonCodeUtil;
        this.msgUtil = msgUtil;
        this.fileStorage = fileStorage;
    }

    /** Bearer 토큰을 검증하고 지정한 범위의 레시피를 조회합니다. */
    @Override
    public RecipeListResultVO recipes(String authorizationHeader, String scope, String query, String categoryCode,
            String authorId, int limit) {
        UserSession session = requireSession(authorizationHeader);
        String normalizedScope = scope == null ? "all" : scope.trim().toLowerCase();
        if (!List.of("all", "mine", "saved").contains(normalizedScope)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msgUtil.getMsg("RECIPE", "001"));
        }
        int safeLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
        List<RecipeVO> recipes = mapper.selectRecipes(session.usrId(), normalizedScope,
                query == null ? "" : query.trim(),
                categoryCode == null || categoryCode.isBlank() ? null : categoryCode.trim(),
                authorId == null || authorId.isBlank() ? null : authorId.trim(), safeLimit,
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

    /** 로그인 세션을 확인하고 재료 공통코드 비고1에 등록된 계량 단위를 반환합니다. */
    @Override
    public List<RecipeIngredientUnitVO> ingredientUnits(String authorizationHeader) {
        requireSession(authorizationHeader);
        List<CommonCodeVO> codes = commonCodeUtil.getAllCodes();
        Set<String> activeUnitCodes = codes.stream()
                .filter(item -> "RCP_UNIT".equalsIgnoreCase(item.getComCd()))
                .map(CommonCodeVO::getComDtlCd)
                .collect(Collectors.toSet());
        List<RecipeIngredientUnitVO> mappings = new ArrayList<>();

        for (CommonCodeVO ingredientCode : codes) {
            if (!isIngredientGroup(ingredientCode.getComCd())
                    || ingredientCode.getRemk1() == null || ingredientCode.getRemk1().isBlank()) {
                continue;
            }

            int sortOrd = 1;
            for (String unitCode : ingredientCode.getRemk1().split("\\|")) {
                String normalizedUnitCode = unitCode.trim();
                if (normalizedUnitCode.isEmpty() || !activeUnitCodes.contains(normalizedUnitCode)) {
                    continue;
                }

                RecipeIngredientUnitVO mapping = new RecipeIngredientUnitVO();
                mapping.setIngredientGroupCode(ingredientCode.getComCd());
                mapping.setIngredientCode(ingredientCode.getComDtlCd());
                mapping.setUnitCode(normalizedUnitCode);
                mapping.setSortOrd(sortOrd++);
                mappings.add(mapping);
            }
        }
        return mappings;
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

    /** JSON 레시피 본문과 완성 사진을 하나의 트랜잭션으로 등록합니다. */
    @Override
    @Transactional
    public Long createRecipeWithPhoto(String authorizationHeader, String requestJson, MultipartFile finishedPhoto) {
        if (requestJson == null || requestJson.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msgUtil.getMsg("COMMON", "023"));
        }

        RecipeCreateRequestVO request;
        try {
            request = objectMapper.readValue(requestJson, RecipeCreateRequestVO.class);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msgUtil.getMsg("COMMON", "023"), exception);
        }

        UserSession session = requireSession(authorizationHeader);
        if (finishedPhoto != null && !finishedPhoto.isEmpty()) {
            String contentType = finishedPhoto.getContentType();
            String normalizedType = "image/jpg".equalsIgnoreCase(contentType) ? "image/jpeg" : contentType;
            String registeredType;
            try {
                registeredType = commonCodeUtil.getCodeNameByValue("CONT_TYP",
                        normalizedType == null ? "" : normalizedType.toLowerCase());
            } catch (IllegalArgumentException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msgUtil.getMsg("FILE", "005"), exception);
            }
            if (!registeredType.startsWith("image/")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msgUtil.getMsg("FILE", "005"));
            }
        }

        Long postSeq = createRecipe(authorizationHeader, request);
        if (finishedPhoto == null || finishedPhoto.isEmpty()) return postSeq;

        Long filSeq = fileStorage.store(finishedPhoto, "003", "001", session.usrId());
        try {
            RecipePhotoVO photo = new RecipePhotoVO();
            photo.setPostSeq(postSeq);
            photo.setFilSeq(filSeq);
            photo.setMediaType(code("MEDIA_TYP", "IMAGE"));
            photo.setSystemUserId(session.usrId());
            requireInserted(mapper.insertRecipePhoto(photo), "완성 사진을 레시피에 연결하지 못했습니다.");
        } catch (RuntimeException exception) {
            try {
                fileStorage.delete(filSeq);
            } catch (RuntimeException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw exception;
        }
        return postSeq;
    }

    /** 작성자 본인 확인 후 레시피 본문·분류·재료·조리 단계를 한 트랜잭션으로 수정합니다. */
    @Override
    @Transactional
    public void updateRecipe(String authorizationHeader, Long postSeq, RecipeCreateRequestVO request) {
        UserSession session = requireSession(authorizationHeader);
        if (request == null || request.getTitle() == null || request.getTitle().isBlank()) {
            throw badRequest("003");
        }
        if (mapper.countOwnedRecipe(postSeq, session.usrId(), code("POST_STAT", "ACTIVE")) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, msgUtil.getMsg("RECIPE", "002"));
        }
        List<RecipeIngredientVO> savedIngredients = mapper.selectIngredients(postSeq);
        List<RecipeStepVO> savedSteps = mapper.selectSteps(postSeq);

        request.setPostSeq(postSeq);
        request.setUsrId(session.usrId());
        request.setSystemUserId(session.usrId());
        request.setTitle(request.getTitle().trim());
        request.setDescription(request.getDescription() == null ? "" : request.getDescription().trim());
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
        prepareIngredients(ingredients, postSeq, session.usrId());

        List<RecipeStepVO> steps = parseSteps(request.getSteps(), postSeq, session.usrId());
        if (steps.isEmpty()) {
            throw badRequest("012");
        }
        preserveStepMetadata(steps, savedSteps);

        requireInserted(mapper.updateRecipePost(request), "레시피 게시물을 수정하지 못했습니다.");
        requireInserted(mapper.updateRecipe(request), "레시피 정보를 수정하지 못했습니다.");
        if (!sameIngredients(ingredients, savedIngredients)) {
            mapper.deleteRecipeIngredients(postSeq);
            for (RecipeIngredientCreateVO ingredient : ingredients) {
                requireInserted(mapper.insertRecipeIngredient(ingredient), "레시피 재료를 수정하지 못했습니다.");
            }
        }
        if (!sameStepContents(steps, savedSteps)) {
            mapper.deleteRecipeSteps(postSeq);
            for (RecipeStepVO step : steps) {
                requireInserted(mapper.insertRecipeStep(step), "레시피 조리 순서를 수정하지 못했습니다.");
            }
        }
    }

    /** 작성자 본인의 레시피와 선택한 새 완성 사진을 하나의 트랜잭션으로 수정합니다. */
    @Override
    @Transactional
    public void updateRecipeWithPhoto(String authorizationHeader, Long postSeq, String requestJson,
            MultipartFile finishedPhoto) {
        if (requestJson == null || requestJson.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msgUtil.getMsg("COMMON", "023"));
        }

        RecipeCreateRequestVO request;
        try {
            request = objectMapper.readValue(requestJson, RecipeCreateRequestVO.class);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msgUtil.getMsg("COMMON", "023"), exception);
        }

        UserSession session = requireSession(authorizationHeader);
        updateRecipe(authorizationHeader, postSeq, request);
        if (finishedPhoto == null || finishedPhoto.isEmpty()) return;

        String contentType = finishedPhoto.getContentType();
        String normalizedType = "image/jpg".equalsIgnoreCase(contentType) ? "image/jpeg" : contentType;
        String registeredType;
        try {
            registeredType = commonCodeUtil.getCodeNameByValue("CONT_TYP",
                    normalizedType == null ? "" : normalizedType.toLowerCase());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msgUtil.getMsg("FILE", "005"), exception);
        }
        if (!registeredType.startsWith("image/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msgUtil.getMsg("FILE", "005"));
        }

        Long filSeq = fileStorage.store(finishedPhoto, "003", "001", session.usrId());
        try {
            RecipePhotoVO photo = new RecipePhotoVO();
            photo.setPostSeq(postSeq);
            photo.setFilSeq(filSeq);
            photo.setMediaType(code("MEDIA_TYP", "IMAGE"));
            photo.setSystemUserId(session.usrId());
            int updatedRows = mapper.updateRecipePhoto(photo);
            if (updatedRows == 0) {
                requireInserted(mapper.insertRecipePhoto(photo), "완성 사진을 레시피에 연결하지 못했습니다.");
            }
        } catch (RuntimeException exception) {
            try {
                fileStorage.delete(filSeq);
            } catch (RuntimeException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw exception;
        }
    }

    /** 작성자 본인의 레시피를 삭제 상태로 바꿔 저장·검색·피드에서 제외합니다. */
    @Override
    @Transactional
    public void deleteRecipe(String authorizationHeader, Long postSeq) {
        UserSession session = requireSession(authorizationHeader);
        String activeStatus = code("POST_STAT", "ACTIVE");
        if (mapper.deleteRecipe(postSeq, session.usrId(), activeStatus, code("POST_STAT", "DELETED")) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, msgUtil.getMsg("RECIPE", "002"));
        }
    }

    /** 읽을 수 있는 레시피에 대해 사용자의 좋아요 상태를 추가하거나 취소합니다. */
    @Override
    @Transactional
    public void setLiked(String authorizationHeader, Long postSeq, boolean enabled) {
        UserSession session = requireSession(authorizationHeader);
        if (mapper.canReadRecipe(postSeq, session.usrId(), code("POST_STAT", "ACTIVE"),
                code("VIS_TYP", "PUBLIC"), code("FOLLOW_STAT", "ACCEPTED")) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, msgUtil.getMsg("RECIPE", "002"));
        }
        if (enabled) {
            mapper.addLike(postSeq, session.usrId());
        } else {
            mapper.removeLike(postSeq, session.usrId());
        }
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
                    if (!isUnitAllowed(groupCode, ingredient.getIngredientCode().trim(),
                            ingredient.getUnitCode().trim())) {
                        throw badRequest("015");
                    }
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
            ingredient.setNote(blankToNull(ingredient.getNote()));
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

    /** 재료 내용이 바뀌지 않은 경우 하위 행을 다시 만들지 않아 기존 시스템 컬럼을 보존합니다. */
    private boolean sameIngredients(List<RecipeIngredientCreateVO> requested, List<RecipeIngredientVO> saved) {
        if (requested.size() != saved.size()) return false;
        for (int index = 0; index < requested.size(); index++) {
            RecipeIngredientCreateVO next = requested.get(index);
            RecipeIngredientVO current = saved.get(index);
            if (!java.util.Objects.equals(next.getIngredientGroupCode(), current.getIngredientGroupCode())
                    || !java.util.Objects.equals(next.getIngredientCode(), current.getIngredientCode())
                    || !java.util.Objects.equals(next.getUnitCode(), current.getUnitCode())
                    || !java.util.Objects.equals(blankToNull(next.getNote()), blankToNull(current.getNote()))
                    || !sameAmount(next.getIngrAmt(), current.getIngrAmt())) {
                return false;
            }
        }
        return true;
    }

    /** 줄바꿈으로 전달된 조리 순서가 기존 내용과 동일한지 확인합니다. */
    private boolean sameStepContents(List<RecipeStepVO> requested, List<RecipeStepVO> saved) {
        if (requested.size() != saved.size()) return false;
        for (int index = 0; index < requested.size(); index++) {
            if (!java.util.Objects.equals(requested.get(index).getContent(), saved.get(index).getContent())) {
                return false;
            }
        }
        return true;
    }

    /** 문구가 유지된 조리 단계의 시간과 사진 연결을 수정 후에도 유지합니다. */
    private void preserveStepMetadata(List<RecipeStepVO> requested, List<RecipeStepVO> saved) {
        boolean[] matched = new boolean[saved.size()];
        for (RecipeStepVO next : requested) {
            for (int index = 0; index < saved.size(); index++) {
                RecipeStepVO current = saved.get(index);
                if (!matched[index] && java.util.Objects.equals(next.getContent(), current.getContent())) {
                    next.setCookSec(current.getCookSec());
                    next.setStepFilSeq(current.getStepFilSeq());
                    matched[index] = true;
                    break;
                }
            }
        }
    }

    /** 수량 값의 스케일 차이를 무시하고 실제 값이 같은지 확인합니다. */
    private boolean sameAmount(BigDecimal left, BigDecimal right) {
        return left == null ? right == null : right != null && left.compareTo(right) == 0;
    }

    private boolean isIngredientGroup(String groupCode) {
        return "RCP_INGR".equals(groupCode)
                || "INGR".equals(groupCode)
                || groupCode.startsWith("CP_INGR_")
                || groupCode.startsWith("RCP_INGR_")
                || groupCode.startsWith("INGR_");
    }

    /** 재료 상세코드의 비고1에 파이프(|)로 등록된 단위인지 확인합니다. */
    private boolean isUnitAllowed(String ingredientGroupCode, String ingredientCode, String unitCode) {
        return commonCodeUtil.getAllCodes().stream()
                .filter(item -> ingredientGroupCode.equalsIgnoreCase(item.getComCd()))
                .filter(item -> ingredientCode.equals(item.getComDtlCd()))
                .map(CommonCodeVO::getRemk1)
                .filter(value -> value != null && !value.isBlank())
                .flatMap(value -> Arrays.stream(value.split("\\|")))
                .map(String::trim)
                .anyMatch(unitCode::equals);
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
