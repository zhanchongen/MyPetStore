package org.csu.mypetstore.admin.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;

/** 订单五状态。数据库存储枚举名，界面使用中文 label。 */
public enum OrderStatus {
    PENDING_PAYMENT("待支付"),
    PAID("待发货"),
    SHIPPED("已发货"),
    COMPLETED("已完成"),
    CANCELLED("已取消");

    @EnumValue
    private final String code;
    private final String label;

    OrderStatus(String label) {
        this.code = name();
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public boolean isPendingPayment() {
        return this == PENDING_PAYMENT;
    }

    public boolean isPaid() {
        return this == PAID;
    }

    public boolean isShipped() {
        return this == SHIPPED;
    }

    public boolean isCompleted() {
        return this == COMPLETED;
    }

    public boolean isCancelled() {
        return this == CANCELLED;
    }

    public boolean canPay() {
        return this == PENDING_PAYMENT;
    }

    public boolean canCancel() {
        return this == PENDING_PAYMENT;
    }

    public boolean canShip() {
        return this == PAID;
    }

    public boolean canComplete() {
        return this == SHIPPED;
    }

    public static OrderStatus from(String value) {
        if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value)) {
            return null;
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
