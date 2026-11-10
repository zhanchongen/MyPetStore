package org.csu.mypetstore.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.csu.mypetstore.domain.enums.UserRole;
import org.csu.mypetstore.entity.StoreUser;
import org.csu.mypetstore.mapper.CartItemMapper;
import org.csu.mypetstore.mapper.StoreUserMapper;
import org.csu.mypetstore.security.AuthInterceptor;
import org.csu.mypetstore.security.SessionUser;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 所有页面共用：当前用户、购物车徽标、CSRF Token。
 */
@ControllerAdvice
public class GlobalModelAdvice {

    private final StoreUserMapper userMapper;
    private final CartItemMapper cartItemMapper;

    public GlobalModelAdvice(StoreUserMapper userMapper, CartItemMapper cartItemMapper) {
        this.userMapper = userMapper;
        this.cartItemMapper = cartItemMapper;
    }

    @ModelAttribute("currentUser")
    public SessionUser currentUser(HttpServletRequest request) {
        Object attribute = request.getAttribute(AuthInterceptor.CURRENT_USER);
        if (attribute instanceof SessionUser user) {
            return user;
        }

        HttpSession session = request.getSession(false);
        SessionUser sessionUser = session == null ? null : (SessionUser) session.getAttribute(AuthInterceptor.SESSION_USER);
        if (sessionUser == null) {
            return null;
        }
        StoreUser user = userMapper.selectById(sessionUser.id());
        if (user == null || user.getRole() != UserRole.CUSTOMER || !user.getStatus().isEnabled()) {
            session.invalidate();
            return null;
        }
        SessionUser current = new SessionUser(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole());
        session.setAttribute(AuthInterceptor.SESSION_USER, current);
        request.setAttribute(AuthInterceptor.CURRENT_USER, current);
        return current;
    }

    @ModelAttribute("cartCount")
    public int cartCount(HttpServletRequest request) {
        SessionUser user = currentUser(request);
        if (user == null) {
            return 0;
        }
        return cartItemMapper.sumQuantityByUser(user.id());
    }

    @ModelAttribute("_csrf")
    public String csrf(HttpServletRequest request) {
        Object token = request.getAttribute("_csrf");
        return token == null ? "" : token.toString();
    }
}
