package org.csu.mypetstore.admin.web;

import jakarta.servlet.http.HttpServletRequest;
import org.csu.mypetstore.admin.security.AdminAuthInterceptor;
import org.csu.mypetstore.admin.security.SessionUser;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** 管理端所有页面共用：当前管理员与 CSRF Token。 */
@ControllerAdvice
public class GlobalModelAdvice {

    @ModelAttribute("currentAdmin")
    public SessionUser currentAdmin(HttpServletRequest request) {
        Object attribute = request.getAttribute(AdminAuthInterceptor.REQUEST_ADMIN);
        return attribute instanceof SessionUser user ? user : null;
    }

    @ModelAttribute("_csrf")
    public String csrf(HttpServletRequest request) {
        Object token = request.getAttribute("_csrf");
        return token == null ? "" : token.toString();
    }
}
