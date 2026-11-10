package org.csu.mypetstore.dto;

/**
 * 开发环境演示用的用户只读视图。
 * 只暴露身份与展示字段，绝不包含密码散列。
 */
public record UserReadResult(
        Long id,
        String username,
        String displayName,
        String role,
        String status) {
}
