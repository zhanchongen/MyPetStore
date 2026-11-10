package org.csu.mypetstore.admin.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;

/** 账号角色，数据库存储 CUSTOMER / ADMIN。 */
public enum UserRole {
    CUSTOMER("普通用户"),
    ADMIN("管理员");

    @EnumValue
    private final String code;
    private final String label;

    UserRole(String label) {
        this.code = name();
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }
}
