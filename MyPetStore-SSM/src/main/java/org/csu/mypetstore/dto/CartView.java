package org.csu.mypetstore.dto;

import java.math.BigDecimal;
import java.util.List;

/** 购物车页数据。 */
public record CartView(
        List<CartLine> lines,
        int totalQuantity,
        BigDecimal validTotal,
        boolean hasInvalid,
        boolean allAvailable) {

    public boolean isEmpty() {
        return lines == null || lines.isEmpty();
    }
}
