package org.csu.mypetstore.admin.web;

import org.csu.mypetstore.admin.common.PageLinks;
import org.csu.mypetstore.admin.common.PageParams;
import org.csu.mypetstore.admin.domain.enums.OrderStatus;
import org.csu.mypetstore.admin.dto.AdminOrderDetail;
import org.csu.mypetstore.admin.dto.AdminOrderRow;
import org.csu.mypetstore.admin.dto.PageResult;
import org.csu.mypetstore.admin.exception.ConflictException;
import org.csu.mypetstore.admin.service.OrderAdminService;
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
@RequestMapping("/orders")
public class OrderController {

    private final OrderAdminService orderService;

    public OrderController(OrderAdminService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String orderNo,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) String page,
                       @RequestParam(required = false) String pageSize,
                       Model model) {
        PageParams params = PageParams.of(page, pageSize, 10);
        PageResult<AdminOrderRow> result = orderService.search(orderNo, status, params.page(), params.pageSize());

        Map<String, String> linkParams = new HashMap<>();
        if (orderNo != null && !orderNo.isBlank()) {
            linkParams.put("orderNo", orderNo.trim());
        }
        if (status != null && !status.isBlank()) {
            linkParams.put("status", status);
        }
        if (pageSize != null && !pageSize.isBlank()) {
            linkParams.put("pageSize", pageSize);
        }

        model.addAttribute("active", "orders");
        model.addAttribute("pageResult", result);
        model.addAttribute("pageLinks", PageLinks.build("/orders", linkParams, params.page(), (int) result.pages()));
        model.addAttribute("orderNo", orderNo == null ? "" : orderNo.trim());
        model.addAttribute("status", status == null ? "" : status);
        model.addAttribute("statusOptions", OrderStatus.values());
        return "OrderList";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        AdminOrderDetail detail = orderService.detail(id);
        model.addAttribute("active", "orders");
        model.addAttribute("detail", detail);
        model.addAttribute("order", detail.getOrder());
        model.addAttribute("items", detail.getItems());
        return "OrderDetail";
    }

    @PostMapping("/{id}/ship")
    public String ship(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            orderService.ship(id);
            redirectAttributes.addFlashAttribute("message", "订单已发货。");
        } catch (ConflictException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/orders/" + id;
    }
}
