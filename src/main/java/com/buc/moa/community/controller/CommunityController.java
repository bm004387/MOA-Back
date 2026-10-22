package com.buc.moa.community.controller;

import com.buc.moa.community.service.CommunityService;
import com.buc.moa.community.vo.CommunityPostCommandVO;
import com.buc.moa.community.vo.CommunityPost;
import com.buc.moa.community.vo.CommunityCommentPreviewVO;
import com.buc.moa.community.vo.CommunityProfileSummaryVO;
import com.buc.moa.community.vo.CommunityUserProfileVO;
import com.buc.moa.community.vo.CommunityUserSearchVO;
import com.buc.moa.community.vo.CommunityFileResourceVO;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 커뮤니티 피드와 게시물 요청을 처리하는 REST 컨트롤러입니다. */
@RestController
@RequestMapping("/api/community")
public class CommunityController {
    private final CommunityService service;
    public CommunityController(CommunityService service) {
        this.service = service;
    }

    /** 선택한 피드 종류의 게시물을 조회합니다. */
    @GetMapping("/feed")
    public List<CommunityPost> feed(
            HttpServletRequest request,
            @RequestParam(defaultValue = "recommended") String type,
            @RequestParam(defaultValue = "20") int limit) {
        return service.feed(service.requireSession(request.getHeader("Authorization")).usrId(), type, limit);
    }

    /** 로그인 사용자의 게시물·팔로워·팔로잉 수를 조회합니다. */
    @GetMapping("/profile/summary")
    public CommunityProfileSummaryVO profileSummary(HttpServletRequest request) {
        return service.profileSummary(service.requireSession(request.getHeader("Authorization")).usrId());
    }

    /** 로그인 사용자의 팔로워 또는 팔로잉 목록을 조회합니다. */
    @GetMapping("/profile/follows")
    public List<CommunityUserSearchVO> followUsers(
            HttpServletRequest request,
            @RequestParam String type,
            @RequestParam(defaultValue = "100") int limit) {
        return service.followUsers(service.requireSession(request.getHeader("Authorization")).usrId(), type, limit);
    }

    /** 게시물 작성자의 프로필과 로그인 사용자의 팔로우 상태를 조회합니다. */
    @GetMapping("/users/{usrId}/profile")
    public CommunityUserProfileVO userProfile(
            HttpServletRequest request,
            @PathVariable String usrId) {
        return service.userProfile(service.requireSession(request.getHeader("Authorization")).usrId(), usrId);
    }

    /** 게시물 작성자의 공개 가능한 게시물 목록을 조회합니다. */
    @GetMapping("/users/{usrId}/posts")
    public List<CommunityPost> userPosts(
            HttpServletRequest request,
            @PathVariable String usrId) {
        return service.userPosts(service.requireSession(request.getHeader("Authorization")).usrId(), usrId);
    }

    /** 로그인 사용자의 팔로우 상태를 변경합니다. */
    @PutMapping("/users/{usrId}/follow")
    public Map<String, Boolean> follow(
            HttpServletRequest request,
            @PathVariable String usrId,
            @RequestBody Toggle body) {
        service.setFollowForSession(request.getHeader("Authorization"), usrId, body.enabled());
        return Map.of("success", true);
    }

    /** 로그인 사용자의 팔로워 목록에서 지정한 계정을 제거합니다. */
    @DeleteMapping("/profile/followers/{usrId}")
    public Map<String, Boolean> removeFollower(
            HttpServletRequest request,
            @PathVariable String usrId) {
        service.removeFollower(service.requireSession(request.getHeader("Authorization")).usrId(), usrId);
        return Map.of("success", true);
    }

    /** 게시물 본문을 검색해 접근 가능한 결과를 반환합니다. */
    @GetMapping("/search/posts")
    public List<CommunityPost> searchPosts(
            HttpServletRequest request,
            @RequestParam String query,
            @RequestParam(defaultValue = "50") int limit) {
        return service.searchPosts(service.requireSession(request.getHeader("Authorization")).usrId(), query, limit);
    }

    /** 게시물이 없는 계정도 포함해 계정 ID와 이름으로 검색합니다. */
    @GetMapping("/search/users")
    public List<CommunityUserSearchVO> searchUsers(
            HttpServletRequest request,
            @RequestParam String query,
            @RequestParam(defaultValue = "discover") String filter,
            @RequestParam(defaultValue = "50") int limit) {
        return service.searchUsers(service.requireSession(request.getHeader("Authorization")).usrId(), query, filter, limit);
    }

    /** 여러 게시물별 최신 댓글 두 건을 한 번에 반환합니다. */
    @GetMapping("/comments/previews")
    public List<CommunityCommentPreviewVO> commentPreviews(
            HttpServletRequest request,
            @RequestParam("postSeq") List<Long> postSeqs) {
        return service.commentPreviews(service.requireSession(request.getHeader("Authorization")).usrId(), postSeqs);
    }

    /** 게시물의 댓글 목록을 반환합니다. */
    @GetMapping("/posts/{postSeq}/comments")
    public List<CommunityPost.CommunityComment> comments(
            HttpServletRequest request,
            @PathVariable Long postSeq) {
        return service.comments(postSeq, service.requireSession(request.getHeader("Authorization")).usrId());
    }

    /** 로그인 사용자의 댓글을 게시물에 등록합니다. */
    @PostMapping("/posts/{postSeq}/comments")
    public Map<String, Boolean> addComment(
            HttpServletRequest request,
            @PathVariable Long postSeq,
            @RequestBody CommentRequest body) {
        CommunityPostCommandVO command = service.newCommand(request.getHeader("Authorization"));
        command.setPostSeq(postSeq);
        command.setCommentContent(body.content());
        command.setParentCmtSeq(body.parentCmtSeq());
        service.addComment(command);
        return Map.of("success", true);
    }

    /** 로그인 사용자가 작성한 댓글을 수정합니다. */
    @PutMapping("/posts/{postSeq}/comments/{cmtSeq}")
    public Map<String, Boolean> updateComment(
            HttpServletRequest request,
            @PathVariable Long postSeq,
            @PathVariable Long cmtSeq,
            @RequestBody CommentRequest body) {
        CommunityPostCommandVO command = service.newCommand(request.getHeader("Authorization"));
        command.setPostSeq(postSeq);
        command.setCmtSeq(cmtSeq);
        command.setCommentContent(body.content());
        service.updateComment(command);
        return Map.of("success", true);
    }

    /** 로그인 사용자가 작성한 댓글을 삭제합니다. */
    @DeleteMapping("/posts/{postSeq}/comments/{cmtSeq}")
    public Map<String, Boolean> deleteComment(
            HttpServletRequest request,
            @PathVariable Long postSeq,
            @PathVariable Long cmtSeq) {
        CommunityPostCommandVO command = service.newCommand(request.getHeader("Authorization"));
        command.setPostSeq(postSeq);
        command.setCmtSeq(cmtSeq);
        service.deleteComment(command);
        return Map.of("success", true);
    }

    /** 본문과 첨부 파일을 새 게시물로 등록합니다. */
    @PostMapping(value = "/posts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Long> create(
            HttpServletRequest request,
            @RequestPart("content") String content,
            @RequestPart(value = "visibility", required = false) String visibility,
            @RequestPart(value = "filTyp", required = false) String requestedFileType,
            @RequestPart(value = "filCd", required = false) String requestedFileCode,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        service.validateFileClassification(
                requestedFileType,
                "003");
        service.validateFileClassification(
                requestedFileCode,
                "001");
        CommunityPostCommandVO command = service.newCommand(request.getHeader("Authorization"));
        command.setContent(content);
        command.setVisibility(visibility);
        command.setFiles(files);
        return Map.of("postSeq", service.create(command));
    }

    /** 사용자가 피드에서 확인한 게시물을 읽음 처리합니다. */
    @PostMapping("/posts/{postSeq}/seen")
    public Map<String, Boolean> seen(
            HttpServletRequest request,
            @PathVariable Long postSeq) {
        CommunityPostCommandVO command = service.newCommand(request.getHeader("Authorization"));
        command.setPostSeq(postSeq);
        service.markSeen(command);
        return Map.of("success", true);
    }

    /** 게시물 좋아요 상태를 변경합니다. */
    @PutMapping("/posts/{postSeq}/like")
    public Map<String, Boolean> like(
            HttpServletRequest request,
            @PathVariable Long postSeq,
            @RequestBody Toggle body) {
        CommunityPostCommandVO command = service.newCommand(request.getHeader("Authorization"));
        command.setPostSeq(postSeq);
        command.setEnabled(body.enabled());
        service.like(command);
        return Map.of("success", true);
    }

    /** 게시물 저장 상태를 변경합니다. */
    @PutMapping("/posts/{postSeq}/save")
    public Map<String, Boolean> save(
            HttpServletRequest request,
            @PathVariable Long postSeq,
            @RequestBody Toggle body) {
        CommunityPostCommandVO command = service.newCommand(request.getHeader("Authorization"));
        command.setPostSeq(postSeq);
        command.setEnabled(body.enabled());
        service.save(command);
        return Map.of("success", true);
    }

    /** 공개 범위를 확인한 뒤 게시물 첨부 파일을 반환합니다. */
    @GetMapping("/files/{filSeq}")
    public ResponseEntity<Resource> file(
            HttpServletRequest request,
            @PathVariable Long filSeq) {
        CommunityFileResourceVO file = service.file(filSeq, service.requireSession(request.getHeader("Authorization")).usrId());
        MediaType contentType = MediaType.parseMediaType(file.contentType());
        return ResponseEntity.ok()
                .contentType(contentType)
                .header("Cache-Control", "private, max-age=3600")
                .body((Resource) new FileSystemResource(file.path()));
    }

    /** 좋아요·저장 변경 요청 값입니다. */
    public record Toggle(boolean enabled) {
    }

    /** 댓글 등록 요청 값입니다. */
    public record CommentRequest(String content, Long parentCmtSeq) {
    }
}
