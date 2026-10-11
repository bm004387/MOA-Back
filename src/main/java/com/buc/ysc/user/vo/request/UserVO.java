package com.buc.ysc.user.vo.request;

import com.buc.ysc.common.vo.CommonVO;
import lombok.Getter;
import lombok.Setter;

/** 사용자 조회·저장 정보와 시스템 감사 컬럼을 담는 VO입니다. */
@Setter
@Getter
public class UserVO extends CommonVO {

    private String usrId;          /* 사용자아이디 */
    private String pwd;          /* 비밀번호 */
    private String pinPwd;        /* BCrypt 암호화 PIN 비밀번호 */
    private String usrNm;          /* 사용자명 */
    private String nckNm;          /* 닉네임 */
    private String rol;            /* 권한 */
    private String hpNo;            /* 핸드폰번호 */
    private String adr;            /* 주소 */
    private String dtlAdr;         /* 상세주소 */
    private String joinDtm;       /* 최초가입일시 */
    private Long prflImgFilSeq;  /* 프로필 이미지 파일 일련번호 */

}
