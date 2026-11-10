package org.csu.mypetstore.web;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.csu.mypetstore.dto.CheckoutConfirm;
import org.csu.mypetstore.dto.CheckoutForm;
import org.csu.mypetstore.entity.StoreUser;
import org.csu.mypetstore.exception.BusinessException;
import org.csu.mypetstore.exception.ConflictException;
import org.csu.mypetstore.security.SessionUser;
import org.csu.mypetstore.service.CheckoutService;
import org.csu.mypetstore.service.OrderService;
import org.csu.mypetstore.service.ProfileService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class CheckoutController {

    public static final String CHECKOUT_CONFIRM = "CHECKOUT_CONFIRM";

    private final CheckoutService checkoutService;
    private final OrderService orderService;
    private final ProfileService profileService;

    public CheckoutController(CheckoutService checkoutService,
                              OrderService orderService,
                              ProfileService profileService) {
        this.checkoutService = checkoutService;
        this.orderService = orderService;
        this.profileService = profileService;
    }

    @GetMapping("/checkout")
    public String checkout(@ModelAttribute("currentUser") SessionUser user,
                           HttpSession session,
                           Model model,
                           RedirectAttributes redirectAttributes) {
        CheckoutConfirm confirm;
        try {
            confirm = checkoutService.prepare(user.id());
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/cart";
        }
        session.setAttribute(CHECKOUT_CONFIRM, confirm);

        StoreUser account = profileService.require(user.id());
        CheckoutForm form = new CheckoutForm();
        form.setReceiverName(account.getDisplayName());
        form.setReceiverPhone(account.getPhone());

        model.addAttribute("confirm", confirm);
        model.addAttribute("form", form);
        return "Checkout";
    }

    @PostMapping("/checkout/submit")
    public String submit(@Valid @ModelAttribute("form") CheckoutForm form,
                         BindingResult bindingResult,
                         @ModelAttribute("currentUser") SessionUser user,
                         HttpSession session,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        CheckoutConfirm confirm = (CheckoutConfirm) session.getAttribute(CHECKOUT_CONFIRM);
        if (confirm == null) {
            redirectAttributes.addFlashAttribute("error", "结算确认已失效，请重新确认。");
            return "redirect:/checkout";
        }
        if (bindingResult.hasErrors()) {
            model.addAttribute("confirm", confirm);
            return "Checkout";
        }
        try {
            long orderId = orderService.placeOrder(user.id(), confirm, form);
            session.removeAttribute(CHECKOUT_CONFIRM);
            redirectAttributes.addFlashAttribute("message", "订单创建成功，请完成模拟支付。");
            return "redirect:/orders/" + orderId;
        } catch (ConflictException ex) {
            session.removeAttribute(CHECKOUT_CONFIRM);
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/checkout";
        } catch (BusinessException ex) {
            session.removeAttribute(CHECKOUT_CONFIRM);
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/cart";
        }
    }
}
