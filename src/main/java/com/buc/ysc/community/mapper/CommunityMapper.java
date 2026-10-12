package com.buc.ysc.community.mapper;

import com.buc.ysc.community.vo.CommunityPost;
import com.buc.ysc.community.vo.CommunityPostCommandVO;
import com.buc.ysc.community.vo.CommunityPostRowVO;
import com.buc.ysc.community.vo.CommunityProfileSummaryVO;
import com.buc.ysc.community.vo.CommunityCommentPreviewVO;
import com.buc.ysc.community.vo.CommunityUserProfileVO;
import com.buc.ysc.community.vo.CommunityUserSearchVO;
import java.util.List;

/** 커뮤니티 SQL 매퍼 인터페이스입니다. */
public interface CommunityMapper {

    /** 내 게시물·팔로워·팔로잉 수를 조회합니다. */
    CommunityProfileSummaryVO selectProfileSummary(
            String usrId,
            String postActive,
            String followAccepted);

    /** 사용자 프로필과 로그인 사용자의 팔로우 상태를 조회합니다. */
    CommunityUserProfileVO selectUserProfile(
            String usrId,
            String viewerUsrId,
            String postActive,
            String followAccepted);

    /** 팔로우 대상 사용자가 존재하는지 확인합니다. */
    int countUser(String usrId);

    /** 사용자의 게시물 피드에 팔로우 사용자 조건을 전달합니다. */
    int followUser(
            String usrId,
            String followingUsrId,
            String followStat);

    /** 팔로우 관계를 해제합니다. */
    int unfollowUser(String usrId, String followingUsrId);

    /** 지정한 팔로워와 현재 사용자의 팔로우 관계를 제거합니다. */
    int removeFollower(
            String usrId,
            String followerUsrId);

    /** 검색어와 관계 필터에 맞는 계정을 조회합니다. */
    List<CommunityUserSearchVO> searchUsers(
            String usrId,
            String query,
            String userFilter,
            int limit,
            String postActive,
            String followAccepted);

    /** 로그인 사용자의 팔로워 또는 팔로잉 목록을 조회합니다. */
    List<CommunityUserSearchVO> selectFollowUsers(
            String usrId,
            String relationType,
            int limit,
            String postActive,
            String followAccepted);

    /** 공개 범위에 맞는 게시물 중 본문 검색 결과를 조회합니다. */
    List<CommunityPostRowVO> searchPosts(
            String usrId,
            String query,
            int limit,
            String postActive,
            String followAccepted,
            String publicVisibility);

    /** 여러 게시물 각각의 최신 댓글을 두 건까지 조회합니다. */
    List<CommunityCommentPreviewVO> selectCommentPreviews(
            String usrId,
            List<Long> postSeqs,
            String postActive,
            String followAccepted,
            String publicVisibility);

    /** 게시물 일련번호를 생성합니다. */
    Long nextPostSeq();

    /** 게시물 본문을 저장합니다. */
    int insertPost(
            Long postSeq,
            CommunityPostCommandVO command,
            String postStat);

    /** 게시물과 파일의 연결 정보를 저장합니다. */
    int insertPostFile(
            Long postSeq,
            Long filSeq,
            int sortOrder,
            String mediaType,
            CommunityPostCommandVO command);

    /** 사용자와 피드 종류에 해당하는 게시물을 조회합니다. */
    List<CommunityPostRowVO> selectFeed(
            String usrId,
            String feedType,
            int limit,
            String profileUsrId,
            String postActive,
            String followAccepted,
            String publicVisibility);

    /** 게시물에 연결된 미디어 정보를 조회합니다. */
    List<CommunityPost.CommunityMedia> selectMedia(Long postSeq);

    /** 게시물 조회 권한을 확인합니다. */
    int canReadPost(
            Long postSeq,
            String usrId,
            String postActive,
            String followAccepted,
            String publicVisibility);

    /** 활성 댓글을 시간순으로 조회합니다. */
    List<CommunityPost.CommunityComment> selectComments(Long postSeq);

    /** 게시물에 속한 활성 부모 댓글인지 확인합니다. */
    int countActiveComment(
            Long postSeq,
            Long cmtSeq);

    /** 댓글 내용을 새로 저장합니다. */
    int insertComment(CommunityPostCommandVO command);

    /** 작성자 본인의 활성 댓글을 수정합니다. */
    int updateComment(CommunityPostCommandVO command);

    /** 작성자 본인의 활성 댓글을 삭제 상태로 변경합니다. */
    int deleteComment(CommunityPostCommandVO command);

    /** 사용자별 게시물 조회 기록을 저장합니다. */
    int markSeen(CommunityPostCommandVO command);

    /** 게시물 좋아요를 추가합니다. */
    int addLike(CommunityPostCommandVO command);

    /** 게시물 좋아요를 삭제합니다. */
    int removeLike(CommunityPostCommandVO command);

    /** 게시물 저장 표시를 추가합니다. */
    int addSave(CommunityPostCommandVO command);

    /** 게시물 저장 표시를 삭제합니다. */
    int removeSave(CommunityPostCommandVO command);

    /** 사용자가 첨부 파일을 조회할 수 있는지 검사합니다. */
    int canReadFile(
            Long filSeq,
            String usrId,
            String postActive,
            String followAccepted,
            String publicVisibility);
}
