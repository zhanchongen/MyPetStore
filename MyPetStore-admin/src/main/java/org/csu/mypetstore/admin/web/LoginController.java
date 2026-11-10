package org.csu.mypetstore.admin.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.csu.mypetstore.admin.security.AdminAuthInterceptor;
import org.csu.mypetstore.admin.security.SessionUser;
import org.csu.mypetstore.admin.service.AdminAuthService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

/** 管理端登录：仅 ENABLED 的 ADMIN 可进入。 */
@Controller
public class LoginController {

    private final AdminAuthService authService;

    public LoginController(AdminAuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/login")
    public String loginPage(@RequestParam(required = false) String returnUrl,
                            @RequestParam(required = false) String expired,
                            @RequestParam(required = false) String logout,
                            HttpServletRequest request,
                            Model model) {
        HttpSession session = request.getSession(false);
        SessionUser current = session == null
                ? null
                : (SessionUser) session.getAttribute(AdminAuthInterceptor.SESSION_ADMIN);
        if (current != null) {
            String target = safeReturnUrl(returnUrl);
            return "redirect:" + (target == null ? "/dashboard" : target);
        }
        if (expired != null) {
            model.addAttribute("error", "登录状态已失效，请重新登录。");
        }
        if (logout != null) {
            model.addAttribute("message", "已退出管理端。");
        }
        model.addAttribute("returnUrl", safeReturnUrl(returnUrl));
        return "Login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String username,
                        @RequestParam String password,
                        @RequestParam(required = false) String returnUrl,
                        HttpServletRequest request,
                        Model model) {
        Optional<SessionUser> user = authService.login(username, password);
        if (user.isEmpty()) {
            model.addAttribute("error", "用户名或密码错误，或该账号没有管理权限。");
            model.addAttribute("username", username == null ? "" : username.trim());
            model.addAttribute("returnUrl", safeReturnUrl(returnUrl));
            return "Login";
        }

        HttpSession oldSession = request.getSession(false);
        if (oldSession != null) {
            oldSession.invalidate();
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(AdminAuthInterceptor.SESSION_ADMIN, user.get());
        String target = safeReturnUrl(returnUrl);
        return "redirect:" + (target == null ? "/dashboard" : target);
    }

    @PostMapping("/logout")
    public String logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return "redirect:/login?logout=1";
    }

    /** 只允许站内相对地址，避免开放重定向。 */
    static String safeReturnUrl(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty() || !value.startsWith("/") || value.startsWith("//")
                || value.contains("\\") || value.contains("\r") || value.contains("\n")) {
            return null;
        }
        if (value.startsWith("/login") || value.startsWith("/logout")) {
            return null;
        }
        return value;
    }
}
