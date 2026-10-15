package com.buc.ysc.security;

import com.buc.ysc.util.MsgUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

/** 필터 체인에서 발생한 401·403을 공통 JSON 응답으로 반환합니다. */
@Component
public class ApiSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final MsgUtil msgUtil;

    public ApiSecurityErrorHandler(MsgUtil msgUtil) {
        this.msgUtil = msgUtil;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        writeError(request, response, HttpStatus.UNAUTHORIZED, getLoginRequiredMessage());
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException exception) throws IOException {
        writeError(request, response, HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");
    }

    private String getLoginRequiredMessage() {
        try {
            String message = msgUtil.getMsg("MYINFO", "009");
            if (message != null && !message.isBlank() && !"MYINFO_009".equals(message)) {
                return message;
            }
        } catch (RuntimeException ignored) {
            // 메시지 저장소가 잠시 불가해도 인증 실패 응답은 반환해야 합니다.
        }
        return "로그인이 필요합니다.";
    }

    private void writeError(HttpServletRequest request, HttpServletResponse response,
            HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Cache-Control", "no-store");

        String body = "{"
                + "\"timestamp\":\"" + escapeJson(Instant.now().toString()) + "\","
                + "\"status\":" + status.value() + ","
                + "\"error\":\"" + escapeJson(status.getReasonPhrase()) + "\","
                + "\"message\":\"" + escapeJson(message) + "\","
                + "\"path\":\"" + escapeJson(request.getRequestURI()) + "\""
                + "}";
        response.getWriter().write(body);
    }

    private String escapeJson(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
    }
}
