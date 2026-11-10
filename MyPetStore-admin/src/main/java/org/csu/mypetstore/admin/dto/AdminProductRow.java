package org.csu.mypetstore.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.csu.mypetstore.admin.domain.enums.ProductStatus;

import java.math.BigDecimal;

/** 商品列表行；分类名称由服务层批量映射，避免模板访问 Mapper。 */
@Data
@AllArgsConstructor
public class AdminProductRow {

    private Long id;
    private String sku;
    private String name;
    private Long categoryId;
    private String categoryName;
    private BigDecimal price;
    private Integer stock;
    private ProductStatus status;
    private Integer sortOrder;
    private Long version;
    private String coverImage;

    public boolean isSoldOut() {
        return stock == null || stock <= 0;
    }
}
