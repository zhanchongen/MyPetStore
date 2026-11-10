package org.csu.mypetstore.admin.web;

import org.csu.mypetstore.admin.common.PageLinks;
import org.csu.mypetstore.admin.common.PageParams;
import org.csu.mypetstore.admin.domain.enums.UserStatus;
import org.csu.mypetstore.admin.dto.AdminUserRow;
import org.csu.mypetstore.admin.dto.PageResult;
import org.csu.mypetstore.admin.exception.BadRequestException;
import org.csu.mypetstore.admin.exception.NotFoundException;
import org.csu.mypetstore.admin.service.UserAdminService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/users")
public class UserController {

    private final UserAdminService userService;

    public UserController(UserAdminService userService) {
        this.userService = userService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String username,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) String page,
                       @RequestParam(required = false) String pageSize,
                       Model model) {
        PageParams params = PageParams.of(page, pageSize, 10);
        PageResult<AdminUserRow> result = userService.search(username, status, params.page(), params.pageSize());

        Map<String, String> linkParams = new HashMap<>();
        if (username != null && !username.isBlank()) {
            linkParams.put("username", username.trim());
        }
        if (status != null && !status.isBlank()) {
            linkParams.put("status", status);
        }
        if (pageSize != null && !pageSize.isBlank()) {
            linkParams.put("pageSize", pageSize);
        }

        model.addAttribute("active", "users");
        model.addAttribute("pageResult", result);
        model.addAttribute("pageLinks", PageLinks.build("/users", linkParams, params.page(), (int) result.pages()));
        model.addAttribute("username", username == null ? "" : username.trim());
        model.addAttribute("status", status == null ? "" : status);
        model.addAttribute("statusOptions", UserStatus.values());
        return "UserList";
    }

    @PostMapping("/{id}/status")
    public String changeStatus(@PathVariable Long id,
                               @RequestParam String target,
                               RedirectAttributes redirectAttributes) {
        try {
            boolean changed = userService.changeStatus(id, target);
            if (changed) {
                redirectAttributes.addFlashAttribute("message",
                        "DISABLED".equals(target) ? "用户已禁用。" : "用户已启用。");
            } else {
                redirectAttributes.addFlashAttribute("message", "用户当前已是该状态，没有修改。");
            }
        } catch (BadRequestException | NotFoundException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/users";
    }
}
