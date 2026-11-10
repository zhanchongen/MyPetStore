package org.csu.mypetstore.admin.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.csu.mypetstore.admin.domain.enums.UserRole;
import org.csu.mypetstore.admin.domain.enums.UserStatus;
import org.csu.mypetstore.admin.entity.StoreUser;
import org.csu.mypetstore.admin.mapper.StoreUserMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 管理端登录拦截：Session 只保存最小身份，每次请求重新查库校验 ADMIN + ENABLED。
 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    public static final String SESSION_ADMIN = "PETSTORE_ADMIN";
    public static final String REQUEST_ADMIN = "currentAdmin";

    private final StoreUserMapper userMapper;

    public AdminAuthInterceptor(StoreUserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        HttpSession session = request.getSession(false);
        SessionUser sessionUser = session == null
                ? null
                : (SessionUser) session.getAttribute(SESSION_ADMIN);

        if (sessionUser == null) {
            redirectToLogin(request, response, false);
            return false;
        }

        StoreUser user = userMapper.selectById(sessionUser.id());
        if (user == null
                || user.getRole() != UserRole.ADMIN
                || user.getStatus() != UserStatus.ENABLED) {
            session.invalidate();
            redirectToLogin(request, response, true);
            return false;
        }

        request.setAttribute(REQUEST_ADMIN,
                new SessionUser(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole()));
        return true;
    }

    private void redirectToLogin(HttpServletRequest request, HttpServletResponse response, boolean expired)
            throws IOException {
        String target = request.getRequestURI();
        if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
            target = target + "?" + request.getQueryString();
        }
        String url = "/login?returnUrl=" + URLEncoder.encode(target, StandardCharsets.UTF_8);
        if (expired) {
            url = url + "&expired=1";
        }
        response.sendRedirect(url);
    }
}
