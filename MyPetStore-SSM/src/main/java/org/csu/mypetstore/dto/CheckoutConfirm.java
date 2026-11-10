package org.csu.mypetstore.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 服务端结算确认快照，只存 Session，不另建表。 */
public record CheckoutConfirm(
        Long userId,
        List<CheckoutLine> lines,
        BigDecimal totalAmount,
        LocalDateTime createdAt) implements Serializable {

    public int totalQuantity() {
        if (lines == null) {
            return 0;
        }
        return lines.stream().mapToInt(line -> line.quantity() == null ? 0 : line.quantity()).sum();
    }

    public record CheckoutLine(
            Long itemId,
            Long productId,
            Integer quantity,
            Long version,
            BigDecimal unitPrice,
            String sku,
            String productName,
            String coverImage) implements Serializable {

        public BigDecimal lineAmount() {
            if (unitPrice == null || quantity == null) {
                return BigDecimal.ZERO;
            }
            return unitPrice.multiply(BigDecimal.valueOf(quantity));
        }
    }
}
