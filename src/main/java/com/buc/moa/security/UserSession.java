package com.buc.moa.security;

/**
 * @param usrId
 * @param usrNm
 * @param rol
 */
public record UserSession(
        String usrId,
        String usrNm,
        String rol,
        String hpNo,
        String adr,
        String dtlAdr
) {
}
