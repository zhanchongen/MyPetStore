package org.csu.mypetstore.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.csu.mypetstore.exception.BusinessException;
import org.csu.mypetstore.security.AuthInterceptor;
import org.csu.mypetstore.security.SessionUser;
import org.csu.mypetstore.service.AuthService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/login")
    public String loginPage(@RequestParam(required = false) String returnUrl,
                            @RequestParam(required = false) String expired,
                            @RequestParam(required = false) String changed,
                            HttpServletRequest request,
                            Model model) {
        HttpSession session = request.getSession(false);
        SessionUser current = session == null ? null : (SessionUser) session.getAttribute(AuthInterceptor.SESSION_USER);
        if (current != null) {
            String target = safeReturnUrl(returnUrl);
            return "redirect:" + (target == null ? "/main" : target);
        }
        if (expired != null) {
            model.addAttribute("error", "登录状态已失效，请重新登录。");
        }
        if (changed != null) {
            model.addAttribute("message", "密码修改成功，请使用新密码重新登录。");
        }
        model.addAttribute("returnUrl", safeReturnUrl(returnUrl));
        return "SignonForm";
    }

    @PostMapping("/login")
    public String login(@RequestParam String username,
                        @RequestParam String password,
                        @RequestParam(required = false) String returnUrl,
                        HttpServletRequest request,
                        Model model) {
        try {
            SessionUser user = authService.loginUser(username, password);
            HttpSession oldSession = request.getSession(false);
            if (oldSession != null) {
                oldSession.invalidate();
            }
            HttpSession session = request.getSession(true);
            session.setAttribute(AuthInterceptor.SESSION_USER, user);

            String target = safeReturnUrl(returnUrl);
            return "redirect:" + (target == null ? "/main" : target);
        } catch (BusinessException ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("username", username == null ? "" : username.trim());
            model.addAttribute("returnUrl", safeReturnUrl(returnUrl));
            return "SignonForm";
        }
    }

    @PostMapping("/logout")
    public String logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return "redirect:/main";
    }

    /** 只允许站内相对地址，防止开放重定向。 */
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
