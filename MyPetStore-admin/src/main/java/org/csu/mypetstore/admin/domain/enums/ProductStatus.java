package org.csu.mypetstore.admin.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;

/** 商品状态：DRAFT 下架，ON_SALE 上架。 */
public enum ProductStatus {
    DRAFT("下架"),
    ON_SALE("上架");

    @EnumValue
    private final String code;
    private final String label;

    ProductStatus(String label) {
        this.code = name();
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public boolean isOnSale() {
        return this == ON_SALE;
    }
}
