package org.csu.mypetstore.web;

import org.csu.mypetstore.common.PageLinks;
import org.csu.mypetstore.common.PageParams;
import org.csu.mypetstore.domain.enums.OrderStatus;
import org.csu.mypetstore.dto.OrderDetail;
import org.csu.mypetstore.dto.OrderRow;
import org.csu.mypetstore.dto.PageResult;
import org.csu.mypetstore.exception.BadRequestException;
import org.csu.mypetstore.exception.BusinessException;
import org.csu.mypetstore.exception.ConflictException;
import org.csu.mypetstore.security.SessionUser;
import org.csu.mypetstore.service.OrderService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

@Controller
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/orders")
    public String list(@ModelAttribute("currentUser") SessionUser user,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) String orderNo,
                       @RequestParam(required = false) String page,
                       @RequestParam(required = false) String pageSize,
                       Model model) {
        OrderStatus parsedStatus = parseStatus(status);
        PageParams pageParams = PageParams.of(page, pageSize, 10);
        PageResult<OrderRow> result = orderService.listUser(
                user.id(), parsedStatus, orderNo, pageParams.page(), pageParams.pageSize());

        Map<String, String> filterParams = new LinkedHashMap<>();
        if (parsedStatus != null) {
            filterParams.put("status", parsedStatus.name());
        }
        if (orderNo != null && !orderNo.trim().isEmpty()) {
            filterParams.put("orderNo", orderNo.trim());
        }
        if (pageParams.pageSize() != 10) {
            filterParams.put("pageSize", String.valueOf(pageParams.pageSize()));
        }

        model.addAttribute("pageResult", result);
        model.addAttribute("pageLinks", PageLinks.build(
                "/orders", filterParams, (int) result.page(), (int) result.pages()));
        model.addAttribute("status", parsedStatus == null ? "ALL" : parsedStatus.name());
        model.addAttribute("orderNo", orderNo == null ? "" : orderNo.trim());
        model.addAttribute("statusOptions", OrderStatus.values());
        return "ListOrders";
    }

    @GetMapping("/orders/{id}")
    public String detail(@ModelAttribute("currentUser") SessionUser user,
                         @PathVariable long id,
                         Model model) {
        OrderDetail detail = orderService.detailForUser(user.id(), id);
        model.addAttribute("detail", detail);
        model.addAttribute("order", detail.order());
        model.addAttribute("items", detail.items());
        return "ViewOrder";
    }

    @PostMapping("/orders/{id}/cancel")
    public String cancel(@ModelAttribute("currentUser") SessionUser user,
                         @PathVariable long id,
                         RedirectAttributes redirectAttributes) {
        try {
            orderService.cancel(user.id(), id);
            redirectAttributes.addFlashAttribute("message", "订单已取消，库存已恢复。");
        } catch (BusinessException | ConflictException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/orders/" + id;
    }

    @PostMapping("/orders/{id}/complete")
    public String complete(@ModelAttribute("currentUser") SessionUser user,
                           @PathVariable long id,
                           RedirectAttributes redirectAttributes) {
        try {
            orderService.complete(user.id(), id);
            redirectAttributes.addFlashAttribute("message", "已确认收货，订单完成。");
        } catch (BusinessException | ConflictException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/orders/" + id;
    }

    private OrderStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank() || "ALL".equalsIgnoreCase(raw)) {
            return null;
        }
        OrderStatus status = OrderStatus.from(raw);
        if (status == null) {
            throw new BadRequestException("订单状态参数不正确。");
        }
        return status;
    }
}
