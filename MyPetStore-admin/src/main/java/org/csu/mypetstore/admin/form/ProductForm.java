package org.csu.mypetstore.admin.form;

import lombok.Data;

import java.math.BigDecimal;

/** 商品新增/编辑表单；编辑时 SKU 与库存只读，status 通过独立上下架操作维护。 */
@Data
public class ProductForm {

    private Long id;
    private String sku;
    private String name;
    private Long categoryId;
    private BigDecimal price;
    private Integer stock;
    private String summary;
    private String description;
    private String coverImage;
    private Integer sortOrder;
    private Long version;
}
