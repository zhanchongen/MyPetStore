package org.csu.mypetstore.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.csu.mypetstore.dto.ProfileForm;
import org.csu.mypetstore.entity.StoreUser;
import org.csu.mypetstore.exception.BadRequestException;
import org.csu.mypetstore.security.AuthInterceptor;
import org.csu.mypetstore.security.SessionUser;
import org.csu.mypetstore.service.ProfileService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/profile")
    public String profile(@ModelAttribute("currentUser") SessionUser current, Model model) {
        StoreUser user = profileService.require(current.id());
        model.addAttribute("user", user);
        model.addAttribute("form", toForm(user));
        return "EditProfile";
    }

    @PostMapping("/profile")
    public String update(@ModelAttribute("currentUser") SessionUser current,
                         @Valid @ModelAttribute("form") ProfileForm form,
                         BindingResult bindingResult,
                         Model model,
                         HttpServletRequest request,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("user", profileService.require(current.id()));
            return "EditProfile";
        }
        try {
            SessionUser updated = profileService.updateProfile(
                    current.id(), form.getDisplayName(), form.getEmail(), form.getPhone());
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.setAttribute(AuthInterceptor.SESSION_USER, updated);
            }
            redirectAttributes.addFlashAttribute("message", "个人资料已更新。");
            return "redirect:/profile";
        } catch (BadRequestException ex) {
            bindingResult.reject("profile", ex.getMessage());
            model.addAttribute("user", profileService.require(current.id()));
            return "EditProfile";
        }
    }

    @GetMapping("/profile/password")
    public String passwordPage() {
        return "ChangePassword";
    }

    @PostMapping("/profile/password")
    public String changePassword(@ModelAttribute("currentUser") SessionUser current,
                                 @RequestParam String oldPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 Model model,
                                 HttpServletRequest request,
                                 RedirectAttributes redirectAttributes) {
        try {
            profileService.changePassword(current.id(), oldPassword, newPassword, confirmPassword);
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            return "redirect:/login?changed=1";
        } catch (BadRequestException ex) {
            model.addAttribute("error", ex.getMessage());
            return "ChangePassword";
        }
    }

    private ProfileForm toForm(StoreUser user) {
        ProfileForm form = new ProfileForm();
        form.setDisplayName(user.getDisplayName());
        form.setEmail(user.getEmail());
        form.setPhone(user.getPhone());
        return form;
    }
}
