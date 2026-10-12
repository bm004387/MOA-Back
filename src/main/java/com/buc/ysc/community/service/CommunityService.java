package com.buc.ysc.community.service;

import com.buc.ysc.community.vo.CommunityPost;
import com.buc.ysc.community.vo.CommunityPostCommandVO;
import com.buc.ysc.community.vo.CommunityProfileSummaryVO;
import com.buc.ysc.community.vo.CommunityCommentPreviewVO;
import com.buc.ysc.community.vo.CommunityUserProfileVO;
import com.buc.ysc.community.vo.CommunityUserSearchVO;
import com.buc.ysc.community.vo.CommunityFileResourceVO;
import com.buc.ysc.security.UserSession;
import java.util.List;

/** 커뮤니티 업무 처리 기능을 정의합니다. */
public interface CommunityService {

    UserSession requireSession(String authorizationHeader);

    CommunityPostCommandVO newCommand(String authorizationHeader);

    void validateFileClassification(String requestedCode, String expectedCode);

    /** 로그인 사용자의 게시물·팔로워·팔로잉 수를 조회합니다. */
    CommunityProfileSummaryVO profileSummary(String userId);

    /** 선택한 사용자의 프로필을 조회합니다. */
    CommunityUserProfileVO userProfile(String viewerUsrId, String profileUsrId);

    /** 선택한 사용자의 게시물을 조회합니다. */
    List<CommunityPost> userPosts(String viewerUsrId, String profileUsrId);

    /** 팔로우 상태를 변경합니다. */
    void setFollow(String usrId, String usrNm, String followingUsrId, boolean enabled);

    void setFollowForSession(String authorizationHeader, String followingUsrId, boolean enabled);

    /** 현재 사용자를 팔로우하는 계정을 팔로워 목록에서 제거합니다. */
    void removeFollower(String usrId, String followerUsrId);

    /** 공개 범위에 맞는 게시물을 검색합니다. */
    List<CommunityPost> searchPosts(String usrId, String query, int limit);

    /** 계정 이름과 ID로 사용자를 검색합니다. */
    List<CommunityUserSearchVO> searchUsers(
            String usrId,
            String query,
            String userFilter,
            int limit);

    /** 로그인 사용자의 팔로워 또는 팔로잉 목록을 조회합니다. */
    List<CommunityUserSearchVO> followUsers(String usrId, String relationType, int limit);

    /** 여러 게시물의 댓글 미리보기를 일괄 조회합니다. */
    List<CommunityCommentPreviewVO> commentPreviews(String userId, List<Long> postSeqs);

    /** 피드 종류에 맞는 게시물과 반응 정보를 조회합니다. */
    List<CommunityPost> feed(String userId, String feedType, int limit);

    /** 사용자가 볼 수 있는 게시물의 댓글을 조회합니다. */
    List<CommunityPost.CommunityComment> comments(Long postSeq, String userId);

    /** 게시물에 새 댓글을 등록합니다. */
    void addComment(CommunityPostCommandVO command);

    /** 작성자 본인의 댓글을 수정합니다. */
    void updateComment(CommunityPostCommandVO command);

    /** 작성자 본인의 댓글을 삭제 상태로 변경합니다. */
    void deleteComment(CommunityPostCommandVO command);

    /** 게시물과 첨부 파일을 저장합니다. */
    Long create(CommunityPostCommandVO command);

    /** 게시물의 조회 완료 기록을 저장합니다. */
    void markSeen(CommunityPostCommandVO command);

    /** 게시물 좋아요 상태를 변경합니다. */
    void like(CommunityPostCommandVO command);

    /** 게시물 저장 상태를 변경합니다. */
    void save(CommunityPostCommandVO command);

    /** 접근 권한을 확인하고 파일 조회 정보를 반환합니다. */
    CommunityFileResourceVO file(Long filSeq, String userId);
}
