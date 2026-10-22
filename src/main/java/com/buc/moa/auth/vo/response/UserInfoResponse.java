package com.buc.moa.auth.vo.response;

public record UserInfoResponse(
        String usrId,
        String usrNm,
        String rol,
        String hpNo,
        String adr,
        String dtlAdr
) {
}
