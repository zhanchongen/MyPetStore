package org.csu.mypetstore.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.csu.mypetstore.domain.enums.UserRole;
import org.csu.mypetstore.entity.StoreUser;
import org.csu.mypetstore.mapper.StoreUserMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 用户端受保护路由：每次请求重新查库，校验角色、启用状态与登录身份。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String SESSION_USER = "PETSTORE_USER";
    public static final String CURRENT_USER = "currentUser";

    private final StoreUserMapper userMapper;

    public AuthInterceptor(StoreUserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        HttpSession session = request.getSession(false);
        SessionUser sessionUser = session == null ? null : (SessionUser) session.getAttribute(SESSION_USER);
        if (sessionUser == null) {
            redirectToLogin(request, response, true);
            return false;
        }

        StoreUser user = userMapper.selectById(sessionUser.id());
        if (user == null || user.getRole() != UserRole.CUSTOMER || !user.getStatus().isEnabled()) {
            if (session != null) {
                session.invalidate();
            }
            response.sendRedirect("/login?expired=1");
            return false;
        }

        SessionUser current = new SessionUser(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole());
        session.setAttribute(SESSION_USER, current);
        request.setAttribute(CURRENT_USER, current);
        return true;
    }

    private void redirectToLogin(HttpServletRequest request,
                                 HttpServletResponse response,
                                 boolean keepReturnUrl) throws Exception {
        String target = "/login";
        if (keepReturnUrl && "GET".equalsIgnoreCase(request.getMethod())) {
            String uri = request.getRequestURI();
            String query = request.getQueryString();
            if (query != null && !query.isBlank()) {
                uri = uri + "?" + query;
            }
            if (uri.startsWith("/") && !uri.startsWith("//")) {
                target = target + "?returnUrl=" + URLEncoder.encode(uri, StandardCharsets.UTF_8);
            }
        }
        response.sendRedirect(target);
    }
}
