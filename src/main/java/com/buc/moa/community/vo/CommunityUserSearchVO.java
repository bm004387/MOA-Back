package com.buc.moa.community.vo;

/** 사용자 검색 결과와 현재 사용자의 팔로우 상태입니다. */
public class CommunityUserSearchVO {
    private String usrId;
    private String usrNm;
    private Long profileImageFilSeq;
    private long postCount;
    private boolean following;

    public String getUsrId() {
        return usrId;
    }

    public void setUsrId(String usrId) {
        this.usrId = usrId;
    }

    public String getUsrNm() {
        return usrNm;
    }

    public void setUsrNm(String usrNm) {
        this.usrNm = usrNm;
    }

    public Long getProfileImageFilSeq() {
        return profileImageFilSeq;
    }

    public void setProfileImageFilSeq(Long profileImageFilSeq) {
        this.profileImageFilSeq = profileImageFilSeq;
    }

    public long getPostCount() {
        return postCount;
    }

    public void setPostCount(long postCount) {
        this.postCount = postCount;
    }

    public boolean isFollowing() {
        return following;
    }

    public void setFollowing(boolean following) {
        this.following = following;
    }
}
