package org.csu.mypetstore.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.csu.mypetstore.domain.enums.ProductStatus;

import java.math.BigDecimal;

/** 购物车行，价格与库存来自当前商品，不信任页面提交值。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartLine {

    private Long itemId;
    private Long productId;
    private Integer quantity;
    private Long version;
    private String sku;
    private String name;
    private String coverImage;
    private BigDecimal price;
    private Integer stock;
    private ProductStatus status;
    private BigDecimal lineAmount;

    public boolean isAvailable() {
        return status == ProductStatus.ON_SALE && stock != null && stock > 0 && quantity != null && quantity <= stock;
    }

    public boolean isSoldOut() {
        return stock == null || stock <= 0;
    }

    public String invalidReason() {
        if (status != ProductStatus.ON_SALE) {
            return "商品已下架";
        }
        if (isSoldOut()) {
            return "商品已售罄";
        }
        if (quantity != null && stock != null && quantity > stock) {
            return "库存不足，当前仅剩 " + stock + " 件";
        }
        return null;
    }
}
