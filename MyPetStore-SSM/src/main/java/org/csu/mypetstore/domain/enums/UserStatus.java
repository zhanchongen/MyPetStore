package org.csu.mypetstore.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;

/** 账号状态，数据库存储 ENABLED / DISABLED。 */
public enum UserStatus {
    ENABLED("启用"),
    DISABLED("禁用");

    @EnumValue
    private final String code;
    private final String label;

    UserStatus(String label) {
        this.code = name();
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public boolean isEnabled() {
        return this == ENABLED;
    }
}
