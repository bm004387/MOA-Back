package com.buc.ysc.community.vo;

/** 커뮤니티 사용자 프로필과 팔로우 상태입니다. */
public class CommunityUserProfileVO {
    private String usrId;
    private String usrNm;
    private String userName;
    private Long profileImageFilSeq;
    private long postCount;
    private long followerCount;
    private long followingCount;
    private boolean following;

    public String getUsrId() { return usrId; }
    public void setUsrId(String usrId) { this.usrId = usrId; }
    public String getUsrNm() { return usrNm; }
    public void setUsrNm(String usrNm) { this.usrNm = usrNm; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public Long getProfileImageFilSeq() { return profileImageFilSeq; }
    public void setProfileImageFilSeq(Long profileImageFilSeq) { this.profileImageFilSeq = profileImageFilSeq; }
    public long getPostCount() { return postCount; }
    public void setPostCount(long postCount) { this.postCount = postCount; }
    public long getFollowerCount() { return followerCount; }
    public void setFollowerCount(long followerCount) { this.followerCount = followerCount; }
    public long getFollowingCount() { return followingCount; }
    public void setFollowingCount(long followingCount) { this.followingCount = followingCount; }
    public boolean isFollowing() { return following; }
    public void setFollowing(boolean following) { this.following = following; }
}
