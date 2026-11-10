package org.csu.mypetstore.service;

import org.csu.mypetstore.dto.CartLine;
import org.csu.mypetstore.dto.CartView;
import org.csu.mypetstore.dto.CheckoutConfirm;
import org.csu.mypetstore.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 生成服务端结算确认快照；确认只存 Session，不锁价、不预占库存。 */
@Service
public class CheckoutService {

    private final CartService cartService;

    public CheckoutService(CartService cartService) {
        this.cartService = cartService;
    }

    public CheckoutConfirm prepare(long userId) {
        CartView cart = cartService.view(userId);
        if (cart.isEmpty()) {
            throw new BusinessException("购物车为空，无法结算。");
        }
        if (cart.hasInvalid()) {
            throw new BusinessException("购物车中存在不可结算商品，请先调整或移除。");
        }

        List<CheckoutConfirm.CheckoutLine> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (CartLine line : cart.lines()) {
            lines.add(new CheckoutConfirm.CheckoutLine(
                    line.getItemId(),
                    line.getProductId(),
                    line.getQuantity(),
                    line.getVersion(),
                    line.getPrice(),
                    line.getSku(),
                    line.getName(),
                    line.getCoverImage()));
            total = total.add(line.getLineAmount());
        }
        return new CheckoutConfirm(userId, lines, total, LocalDateTime.now());
    }
}
