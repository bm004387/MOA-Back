package com.buc.moa.auth.vo.record;

public record UsrIdCheckResponse(
        boolean available,
        String message
) {
}