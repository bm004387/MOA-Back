package com.buc.moa.recipe.service.impl;

import com.buc.moa.recipe.mapper.RecipeMapper;
import com.buc.moa.recipe.service.RecipeService;
import com.buc.moa.recipe.vo.RecipeVO;
import com.buc.moa.security.SessionManager;
import com.buc.moa.security.UserSession;
import com.buc.moa.util.CommonCodeUtil;
import com.buc.moa.util.MsgUtil;
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
    public List<RecipeVO> recipes(String authorizationHeader, String scope, String query, String categoryCode,
            int limit) {
        UserSession session = requireSession(authorizationHeader);
        String normalizedScope = scope == null ? "all" : scope.trim().toLowerCase();
        if (!List.of("all", "mine", "saved").contains(normalizedScope)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msgUtil.getMsg("RECIPE", "001"));
        }
        int safeLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
        return mapper.selectRecipes(session.usrId(), normalizedScope,
                query == null ? "" : query.trim(),
                categoryCode == null || categoryCode.isBlank() ? null : categoryCode.trim(), safeLimit,
                code("POST_STAT", "ACTIVE"), code("VIS_TYP", "PUBLIC"), code("FOLLOW_STAT", "ACCEPTED"));
    }

    /** 공개 범위와 게시 상태를 확인한 뒤 재료와 조리 순서를 포함해 반환합니다. */
    @Override
    public RecipeVO recipe(String authorizationHeader, Long postSeq) {
        UserSession session = requireSession(authorizationHeader);
        if (mapper.canReadRecipe(postSeq, session.usrId(), code("POST_STAT", "ACTIVE"),
                code("VIS_TYP", "PUBLIC"), code("FOLLOW_STAT", "ACCEPTED")) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, msgUtil.getMsg("RECIPE", "002"));
        }
        RecipeVO recipe = mapper.selectRecipe(postSeq, session.usrId());
        if (recipe == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, msgUtil.getMsg("RECIPE", "002"));
        }
        recipe.setIngredients(mapper.selectIngredients(postSeq));
        recipe.setSteps(mapper.selectSteps(postSeq));
        return recipe;
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
}
