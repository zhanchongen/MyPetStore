package org.csu.mypetstore.domain.enums;

/** 商品类型筛选：类型来自既有二级分类（宠物 / 用品），不新增数据库字段。 */
public enum ProductType {
    ALL("全部"),
    PETS("宠物"),
    SUPPLIES("用品");

    private final String label;

    ProductType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static ProductType from(String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
