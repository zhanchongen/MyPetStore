package org.csu.mypetstore.domain.enums;

/** 商品列表排序白名单。 */
public enum ProductSort {
    DEFAULT("默认排序"),
    PRICE_ASC("价格从低到高"),
    PRICE_DESC("价格从高到低"),
    NEWEST("新品优先");

    private final String label;

    ProductSort(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static ProductSort from(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT;
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
