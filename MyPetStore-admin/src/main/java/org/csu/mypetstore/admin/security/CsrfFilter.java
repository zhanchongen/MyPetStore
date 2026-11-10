package org.csu.mypetstore.admin.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 会话级 CSRF Token：GET 建立并写入请求属性，所有写请求必须携带 _csrf。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class CsrfFilter extends OncePerRequestFilter {

    public static final String SESSION_ATTR = "CSRF_TOKEN";
    public static final String REQUEST_ATTR = "_csrf";
    public static final String PARAM_NAME = "_csrf";

    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (isStatic(path) || path.startsWith("/error")) {
            filterChain.doFilter(request, response);
            return;
        }

        HttpSession session = request.getSession(true);
        String token = (String) session.getAttribute(SESSION_ATTR);
        if (token == null) {
            token = newToken();
            session.setAttribute(SESSION_ATTR, token);
        }
        request.setAttribute(REQUEST_ATTR, token);

        if (isWriteMethod(request.getMethod())) {
            String submitted = request.getParameter(PARAM_NAME);
            if (submitted == null || !constantTimeEquals(token, submitted)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "表单校验失败，请刷新页面后重试。");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean isStatic(String path) {
        return path.startsWith("/css/")
                || path.startsWith("/js/")
                || path.startsWith("/images/")
                || path.startsWith("/webjars/")
                || path.equals("/favicon.ico");
    }

    private boolean isWriteMethod(String method) {
        return "POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method);
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }
}
