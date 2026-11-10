package org.csu.mypetstore.admin.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** 控制台只提供入口，不展示订单量/销售额/会员数等假统计。 */
@Controller
public class DashboardController {

    @GetMapping({"/", "/dashboard"})
    public String dashboard(Model model) {
        model.addAttribute("active", "dashboard");
        return "Dashboard";
    }
}
