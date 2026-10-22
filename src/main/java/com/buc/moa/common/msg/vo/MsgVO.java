package com.buc.moa.common.msg.vo;

import com.buc.moa.common.vo.CommonVO;
import lombok.Getter;
import lombok.Setter;

/** 메시지 조회 결과와 시스템 감사 컬럼을 보관하는 VO입니다. */
@Getter
@Setter
public class MsgVO extends CommonVO {

    private String menuId;
    private String msgCd;
    private String msgTp;
    private String msgCn;
    private String useYn;
    private String msgDesc;
}
