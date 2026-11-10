package org.csu.mypetstore.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.csu.mypetstore.domain.enums.ProductStatus;

import java.math.BigDecimal;

/** 商品卡片/列表行。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductCard {

    private Long id;
    private String sku;
    private String name;
    private String summary;
    private String coverImage;
    private BigDecimal price;
    private Integer stock;
    private ProductStatus status;
    private String categoryName;
    private String topCategoryName;

    public boolean isSoldOut() {
        return stock == null || stock <= 0;
    }
}
