package com.buc.moa.community.service.impl;

import com.buc.moa.community.mapper.CommunityMapper;
import com.buc.moa.util.CommonCodeUtil;
import com.buc.moa.community.service.CommunityService;
import com.buc.moa.community.vo.CommunityPost;
import com.buc.moa.community.vo.CommunityPostCommandVO;
import com.buc.moa.community.vo.CommunityPostRowVO;
import com.buc.moa.community.vo.CommunityProfileSummaryVO;
import com.buc.moa.community.vo.CommunityCommentPreviewVO;
import com.buc.moa.community.vo.CommunityUserProfileVO;
import com.buc.moa.community.vo.CommunityUserSearchVO;
import com.buc.moa.community.vo.CommunityFileResourceVO;
import com.buc.moa.file.vo.StoredFile;
import com.buc.moa.security.SessionManager;
import com.buc.moa.security.UserSession;
import com.buc.moa.file.service.FileStorageService;
import com.buc.moa.notification.NewFollowerEvent;
import org.springframework.context.ApplicationEventPublisher;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** 커뮤니티 업무 규칙과 파일 저장 처리를 구현합니다. */
@Service
public class CommunityServiceImpl implements CommunityService {

    private final CommunityMapper mapper;
    private final FileStorageService fileStorage;
    private final ApplicationEventPublisher eventPublisher;
    private final CommonCodeUtil commonCodeUtil;
    private final SessionManager sessionManager;

    public CommunityServiceImpl(
            CommunityMapper mapper,
            FileStorageService fileStorage,
            ApplicationEventPublisher eventPublisher,
            CommonCodeUtil commonCodeUtil,
            SessionManager sessionManager) {
        this.mapper = mapper;
        this.fileStorage = fileStorage;
        this.eventPublisher = eventPublisher;
        this.commonCodeUtil = commonCodeUtil;
        this.sessionManager = sessionManager;
    }

    /** Authorization 헤더에서 로그인 세션을 검증합니다. */
    @Override
    public UserSession requireSession(String authorizationHeader) {
        UserSession session = authorizationHeader != null && authorizationHeader.startsWith("Bearer ")
                ? sessionManager.getSession(authorizationHeader.substring(7))
                : null;
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        return session;
    }

    /** 로그인 세션의 사용자 ID를 본문과 시스템 컬럼에 설정한 게시물 요청 VO를 만듭니다. */
    @Override
    public CommunityPostCommandVO newCommand(String authorizationHeader) {
        UserSession session = requireSession(authorizationHeader);
        CommunityPostCommandVO command = new CommunityPostCommandVO();
        command.setUsrId(session.usrId());
        command.setSystemUserId(session.usrId());
        return command;
    }

    /** 요청에서 전달된 파일 분류 코드가 업무 코드와 일치하는지 확인합니다. */
    @Override
    public void validateFileClassification(String requestedCode, String expectedCode) {
        if (requestedCode != null && !requestedCode.equals(expectedCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "파일 분류 코드가 올바르지 않습니다.");
        }
    }

    /** 로그인 사용자의 게시물·팔로워·팔로잉 수를 조회합니다. */
    @Override
    public CommunityProfileSummaryVO profileSummary(String userId) {
        return mapper.selectProfileSummary(
                userId,
                code("POST_STAT", "ACTIVE"),
                code("FOLLOW_STAT", "ACCEPTED"));
    }

    /** 대상 사용자 존재 여부를 확인하고 프로필 통계를 조회합니다. */
    @Override
    public CommunityUserProfileVO userProfile(String viewerUsrId, String profileUsrId) {
        CommunityUserProfileVO profile = mapper.selectUserProfile(
                profileUsrId,
                viewerUsrId,
                code("POST_STAT", "ACTIVE"),
                code("FOLLOW_STAT", "ACCEPTED"));
        if (profile == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다.");
        }
        return profile;
    }

    /** 기존 피드 조회 규칙으로 대상 사용자의 공개 가능한 게시물을 조회합니다. */
    @Override
    public List<CommunityPost> userPosts(String viewerUsrId, String profileUsrId) {
        if (mapper.countUser(profileUsrId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다.");
        }
        return mapPosts(mapper.selectFeed(
                viewerUsrId,
                "profile",
                50,
                profileUsrId,
                code("POST_STAT", "ACTIVE"),
                code("FOLLOW_STAT", "ACCEPTED"),
                code("VIS_TYP", "PUBLIC")));
    }

    /** 자기 자신 팔로우를 차단하고 팔로우 상태를 변경합니다. */
    @Override
    @Transactional
    public void setFollow(String usrId, String usrNm, String followingUsrId, boolean enabled) {
        if (usrId.equals(followingUsrId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "자기 자신은 팔로우할 수 없습니다.");
        }
        if (mapper.countUser(followingUsrId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다.");
        }
        if (enabled) {
            int changed = mapper.followUser(
                    usrId,
                    followingUsrId,
                    code("FOLLOW_STAT", "ACCEPTED"));
            if (changed > 0) {
                eventPublisher.publishEvent(
                        new NewFollowerEvent(followingUsrId, usrId, usrNm));
            }
        } else {
            mapper.unfollowUser(usrId, followingUsrId);
        }
    }

    /** Authorization 헤더에서 팔로워 정보를 확인해 팔로우 상태를 변경합니다. */
    @Override
    public void setFollowForSession(String authorizationHeader, String followingUsrId, boolean enabled) {
        UserSession session = requireSession(authorizationHeader);
        setFollow(session.usrId(), session.usrNm(), followingUsrId, enabled);
    }

    /** 현재 사용자를 팔로우 중인 계정의 관계만 제거합니다. */
    @Override
    @Transactional
    public void removeFollower(String usrId, String followerUsrId) {
        if (usrId.equals(followerUsrId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "자기 자신은 팔로워에서 제거할 수 없습니다.");
        }
        mapper.removeFollower(usrId, followerUsrId);
    }

    /** 공개 범위에 따라 읽을 수 있는 게시물 본문을 검색합니다. */
    @Override
    public List<CommunityPost> searchPosts(String usrId, String query, int limit) {
        String keyword = normalizeSearchQuery(query);
        return mapPosts(mapper.searchPosts(
                usrId,
                keyword,
                Math.max(1, Math.min(limit, 50)),
                code("POST_STAT", "ACTIVE"),
                code("FOLLOW_STAT", "ACCEPTED"),
                code("VIS_TYP", "PUBLIC")));
    }

    /** 계정 테이블을 직접 검색하여 게시물이 없는 사용자도 반환합니다. */
    @Override
    public List<CommunityUserSearchVO> searchUsers(
            String usrId,
            String query,
            String userFilter,
            int limit) {
        String keyword = normalizeSearchQuery(query);
        String filter = List.of("discover", "following", "followers").contains(userFilter)
                ? userFilter
                : "discover";
        return mapper.searchUsers(
                usrId,
                keyword,
                filter,
                Math.max(1, Math.min(limit, 50)),
                code("POST_STAT", "ACTIVE"),
                code("FOLLOW_STAT", "ACCEPTED"));
    }

    /** 관계 종류를 검증한 뒤 로그인 사용자의 목록만 조회합니다. */
    @Override
    public List<CommunityUserSearchVO> followUsers(String usrId, String relationType, int limit) {
        if (!List.of("followers", "following").contains(relationType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "팔로우 목록 종류가 올바르지 않습니다.");
        }
        return mapper.selectFollowUsers(
                usrId,
                relationType,
                Math.max(1, Math.min(limit, 100)),
                code("POST_STAT", "ACTIVE"),
                code("FOLLOW_STAT", "ACCEPTED"));
    }

    private String normalizeSearchQuery(String query) {
        if (query == null || query.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "검색어를 입력해 주세요.");
        }
        String keyword = query.trim();
        if (keyword.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "검색어는 100자까지 입력할 수 있습니다.");
        }
        return keyword;
    }

    private int canReadPost(Long postSeq, String userId) {
        Map<String, String> codes = postAccessCodes();
        return mapper.canReadPost(
                postSeq,
                userId,
                codes.get("postActive"),
                codes.get("followAccepted"),
                codes.get("publicVisibility"));
    }

    /** 댓글·읽음·반응 요청 전에 게시물 공개 권한을 확인합니다. */
    private void requireReadablePost(Long postSeq, String userId) {
        if (canReadPost(postSeq, userId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "게시물을 찾을 수 없습니다.");
        }
    }

    private Map<String, String> postAccessCodes() {
        return Map.of(
                "postActive", code("POST_STAT", "ACTIVE"),
                "followAccepted", code("FOLLOW_STAT", "ACCEPTED"),
                "publicVisibility", code("VIS_TYP", "PUBLIC"));
    }

    /** 공통코드명에 해당하는 분류별 상세코드 값을 반환합니다. */
    private String code(String group, String name) {
        return commonCodeUtil.getDetailCode(group, name);
    }

    private List<CommunityPost> mapPosts(List<CommunityPostRowVO> rows) {
        return rows.stream()
                .map(row -> new CommunityPost(
                        row.getPostSeq(), row.getAuthorId(), row.getAuthorName(),
                        row.getProfileImageFilSeq(), row.getContent(), row.getCreatedAt(),
                        row.getLikeCount(), row.getCommentCount(), row.isLikedByMe(),
                        row.isSavedByMe(), mapper.selectMedia(row.getPostSeq())))
                .toList();
    }

    /** 게시물 ID를 확인하고 댓글 미리보기를 일괄 조회합니다. */
    @Override
    public List<CommunityCommentPreviewVO> commentPreviews(String userId, List<Long> postSeqs) {
        if (postSeqs == null || postSeqs.isEmpty()) {
            return List.of();
        }
        if (postSeqs.size() > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "게시물은 한 번에 50개까지 조회할 수 있습니다.");
        }
        return mapper.selectCommentPreviews(
                userId,
                postSeqs.stream().distinct().toList(),
                code("POST_STAT", "ACTIVE"),
                code("FOLLOW_STAT", "ACCEPTED"),
                code("VIS_TYP", "PUBLIC"));
    }

    /** 피드 결과에 첨부 파일 정보를 합쳐 반환합니다. */
    @Override
    public List<CommunityPost> feed(String userId, String feedType, int limit) {
        String type = List.of("recommended", "following", "popular", "mine", "saved").contains(feedType)
                ? feedType
                : "recommended";
        List<CommunityPostRowVO> rows = mapper.selectFeed(
                userId,
                type,
                Math.max(1, Math.min(limit, 50)),
                userId,
                code("POST_STAT", "ACTIVE"),
                code("FOLLOW_STAT", "ACCEPTED"),
                code("VIS_TYP", "PUBLIC"));
        return mapPosts(rows);
    }

    /** 게시물 공개 권한을 검사한 뒤 활성 댓글을 반환합니다. */
    @Override
    public List<CommunityPost.CommunityComment> comments(Long postSeq, String userId) {
        requireReadablePost(postSeq, userId);
        return mapper.selectComments(postSeq);
    }

    /** 게시물 공개 권한과 내용을 확인한 뒤 댓글을 등록합니다. */
    @Override
    @Transactional
    public void addComment(CommunityPostCommandVO command) {
        requireReadablePost(command.getPostSeq(), command.getUsrId());
        if (command.getCommentContent() == null || command.getCommentContent().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "댓글 내용을 입력해 주세요.");
        }
        if (command.getParentCmtSeq() != null
                && mapper.countActiveComment(command.getPostSeq(), command.getParentCmtSeq()) == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "답글을 달 댓글을 찾을 수 없습니다.");
        }
        command.setCommentContent(command.getCommentContent().trim());
        mapper.insertComment(command);
    }

    /** 로그인 사용자가 작성한 활성 댓글만 수정합니다. */
    @Override
    @Transactional
    public void updateComment(CommunityPostCommandVO command) {
        requireReadablePost(command.getPostSeq(), command.getUsrId());
        if (command.getCommentContent() == null || command.getCommentContent().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "댓글 내용을 입력해 주세요.");
        }
        command.setCommentContent(command.getCommentContent().trim());
        if (mapper.updateComment(command) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "본인이 작성한 댓글만 수정할 수 있습니다.");
        }
    }

    /** 로그인 사용자가 작성한 댓글만 삭제 상태로 변경합니다. */
    @Override
    @Transactional
    public void deleteComment(CommunityPostCommandVO command) {
        requireReadablePost(command.getPostSeq(), command.getUsrId());
        if (mapper.deleteComment(command) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "본인이 작성한 댓글만 삭제할 수 있습니다.");
        }
    }

    /** 게시물과 업로드된 미디어를 트랜잭션으로 저장합니다. */
    @Override
    @Transactional
    public Long create(CommunityPostCommandVO command) {
        String userId = command.getUsrId();
        String content = command.getContent();
        if (content == null || content.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "게시물 내용을 입력해 주세요.");
        }
        String followerVisibility = code("VIS_TYP", "FOLLOWER");
        String publicVisibility = code("VIS_TYP", "PUBLIC");
        String vis = followerVisibility.equalsIgnoreCase(command.getVisibility())
                ? followerVisibility
                : publicVisibility;
        Long seq = mapper.nextPostSeq();
        command.setContent(content.trim());
        command.setVisibility(vis);
        mapper.insertPost(seq, command, code("POST_STAT", "ACTIVE"));
        List<Long> stored = new ArrayList<>();
        try {
            int order = 1;
            for (MultipartFile file : command.getFiles() == null
                    ? List.<MultipartFile>of()
                    : command.getFiles()) {
                if (file == null || file.isEmpty()) {
                    continue;
                }
                String contentType = file.getContentType();
                String registeredContentType;
                try {
                    registeredContentType = commonCodeUtil.getCodeNameByValue(
                            "CONT_TYP",
                            contentType == null ? "" : contentType.toLowerCase());
                } catch (IllegalArgumentException exception) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "등록되지 않은 파일 형식입니다.",
                            exception);
                }
                if (!registeredContentType.startsWith("image/")
                        && !registeredContentType.startsWith("video/")) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "게시물에는 이미지 또는 동영상만 첨부할 수 있습니다.");
                }
                String type = registeredContentType.startsWith("video/")
                        ? code("MEDIA_TYP", "VIDEO")
                        : code("MEDIA_TYP", "IMAGE");
                String fileType = "003";
                String fileCode = "001";
                Long filSeq = fileStorage.store(file, fileType, fileCode, userId);
                stored.add(filSeq);
                mapper.insertPostFile(seq, filSeq, order++, type, command);
            }
        } catch (RuntimeException ex) {
            stored.forEach(fileStorage::delete);
            throw ex;
        }
        return seq;
    }

    /** 게시물 열람 기록을 저장합니다. */
    @Override
    @Transactional
    public void markSeen(CommunityPostCommandVO command) {
        requireReadablePost(command.getPostSeq(), command.getUsrId());
        mapper.markSeen(command);
    }

    /** 파일 접근 권한을 확인한 뒤 공통코드의 MIME값과 저장 경로를 반환합니다. */
    @Override
    public CommunityFileResourceVO file(Long filSeq, String userId) {
        if (mapper.canReadFile(filSeq, userId, code("POST_STAT", "ACTIVE"),
                code("FOLLOW_STAT", "ACCEPTED"), code("VIS_TYP", "PUBLIC")) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "파일을 찾을 수 없습니다.");
        }
        StoredFile file = fileStorage.metadata(filSeq);
        String contentType = commonCodeUtil.getCodeName("CONT_TYP", file.getContTyp());
        return new CommunityFileResourceVO(fileStorage.pathOf(file).toString(), contentType);
    }

    /** 게시물 좋아요를 등록하거나 취소합니다. */
    @Override
    @Transactional
    public void like(CommunityPostCommandVO command) {
        requireReadablePost(command.getPostSeq(), command.getUsrId());
        if (Boolean.TRUE.equals(command.getEnabled())) {
            mapper.addLike(command);
        } else {
            mapper.removeLike(command);
        }
    }

    /** 게시물 저장 표시를 등록하거나 취소합니다. */
    @Override
    @Transactional
    public void save(CommunityPostCommandVO command) {
        requireReadablePost(command.getPostSeq(), command.getUsrId());
        if (Boolean.TRUE.equals(command.getEnabled())) {
            mapper.addSave(command);
        } else {
            mapper.removeSave(command);
        }
    }
}
