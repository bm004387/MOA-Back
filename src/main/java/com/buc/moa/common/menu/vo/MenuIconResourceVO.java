package com.buc.moa.common.menu.vo;

import java.nio.file.Path;

/** 메뉴 아이콘 파일의 실제 경로와 응답 MIME 타입입니다. */
public record MenuIconResourceVO(Path path, String contentType) {
}
