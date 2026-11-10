package org.csu.mypetstore.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.csu.mypetstore.admin.domain.enums.ProductStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "product", autoResultMap = true)
public class Product {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sku;
    private Long categoryId;
    private String name;
    private String summary;
    private String description;
    private String coverImage;
    private BigDecimal price;
    private Integer stock;
    private ProductStatus status;
    private Integer sortOrder;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public boolean isOnSale() {
        return status == ProductStatus.ON_SALE;
    }

    public boolean isSoldOut() {
        return stock != null && stock <= 0;
    }
}
