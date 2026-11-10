package org.csu.mypetstore.web;

import org.csu.mypetstore.domain.enums.OrderStatus;
import org.csu.mypetstore.dto.OrderDetail;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PaymentController {

    private final OrderService orderService;

    public PaymentController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/payment/{id}")
    public String payment(@ModelAttribute("currentUser") SessionUser user,
                          @PathVariable long id,
                          Model model,
                          RedirectAttributes redirectAttributes) {
        OrderDetail detail = orderService.detailForUser(user.id(), id);
        if (detail.order().getStatus() != OrderStatus.PENDING_PAYMENT) {
            redirectAttributes.addFlashAttribute("error", "当前订单状态不可支付。");
            return "redirect:/orders/" + id;
        }
        model.addAttribute("order", detail.order());
        return "Payment";
    }

    @PostMapping("/payment/{id}/success")
    public String paySuccess(@ModelAttribute("currentUser") SessionUser user,
                             @PathVariable long id,
                             RedirectAttributes redirectAttributes) {
        try {
            OrderService.PaymentResult result = orderService.pay(user.id(), id);
            if (result == OrderService.PaymentResult.SUCCESS) {
                redirectAttributes.addFlashAttribute("message", "模拟支付成功，订单已进入待发货。");
            } else {
                redirectAttributes.addFlashAttribute("message", "订单已支付，保留首次支付凭证。");
            }
        } catch (BusinessException | ConflictException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/orders/" + id;
    }

    @PostMapping("/payment/{id}/fail")
    public String payFail(@ModelAttribute("currentUser") SessionUser user,
                          @PathVariable long id,
                          RedirectAttributes redirectAttributes) {
        try {
            orderService.failPayment(user.id(), id);
            redirectAttributes.addFlashAttribute("error", "模拟支付失败，订单仍待支付，可重试或取消。");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/orders/" + id;
    }
}
