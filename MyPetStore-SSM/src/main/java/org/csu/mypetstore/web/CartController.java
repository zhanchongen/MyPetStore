package org.csu.mypetstore.web;

import org.csu.mypetstore.dto.CartView;
import org.csu.mypetstore.exception.BusinessException;
import org.csu.mypetstore.exception.ConflictException;
import org.csu.mypetstore.security.SessionUser;
import org.csu.mypetstore.service.CartService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping("/cart")
    public String cart(@ModelAttribute("currentUser") SessionUser user, Model model) {
        CartView cart = cartService.view(user.id());
        model.addAttribute("cart", cart);
        return "Cart";
    }

    @PostMapping("/cart/add")
    public String add(@ModelAttribute("currentUser") SessionUser user,
                      @RequestParam long productId,
                      @RequestParam int quantity,
                      RedirectAttributes redirectAttributes) {
        try {
            cartService.add(user.id(), productId, quantity);
            redirectAttributes.addFlashAttribute("message", "已加入购物车。");
            return "redirect:/cart";
        } catch (BusinessException | ConflictException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/product/" + productId;
        }
    }

    @PostMapping("/cart/update")
    public String update(@ModelAttribute("currentUser") SessionUser user,
                         @RequestParam long itemId,
                         @RequestParam int quantity,
                         @RequestParam long version,
                         RedirectAttributes redirectAttributes) {
        try {
            cartService.update(user.id(), itemId, quantity, version);
            redirectAttributes.addFlashAttribute("message", "购物车数量已更新。");
        } catch (BusinessException | ConflictException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/cart";
    }

    @PostMapping("/cart/remove")
    public String remove(@ModelAttribute("currentUser") SessionUser user,
                         @RequestParam long itemId,
                         @RequestParam long version,
                         RedirectAttributes redirectAttributes) {
        try {
            cartService.remove(user.id(), itemId, version);
            redirectAttributes.addFlashAttribute("message", "商品已从购物车移除。");
        } catch (BusinessException | ConflictException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/cart";
    }
}
