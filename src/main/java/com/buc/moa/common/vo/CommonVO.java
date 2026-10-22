package com.buc.moa.common.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;

/** DB 등록·수정 시 사용하는 공통 시스템 컬럼 VO입니다. */
public abstract class CommonVO {

    /** 세션에서 전달받은 시스템 사용자 ID입니다. */
    private String userId;
    private String frstRegUserId;
    private String frstRegDtm;
    private String lastModUserId;
    private String lastModDtm;

    /** 세션 사용자 ID를 등록자와 수정자로 설정합니다. */
    public void setSystemUserId(String userId) {
        this.userId = userId;
        this.frstRegUserId = userId;
        this.lastModUserId = userId;
    }

    @JsonIgnore
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    @JsonIgnore
    public String getFrstRegUserId() {
        return frstRegUserId;
    }

    public void setFrstRegUserId(String frstRegUserId) {
        this.frstRegUserId = frstRegUserId;
    }

    @JsonIgnore
    public String getFrstRegDtm() {
        return frstRegDtm;
    }

    public void setFrstRegDtm(String frstRegDtm) {
        this.frstRegDtm = frstRegDtm;
    }

    @JsonIgnore
    public String getLastModUserId() {
        return lastModUserId;
    }

    public void setLastModUserId(String lastModUserId) {
        this.lastModUserId = lastModUserId;
    }

    @JsonIgnore
    public String getLastModDtm() {
        return lastModDtm;
    }

    public void setLastModDtm(String lastModDtm) {
        this.lastModDtm = lastModDtm;
    }
}
