package org.csu.mypetstore.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.csu.mypetstore.admin.domain.enums.UserStatus;

import java.time.LocalDateTime;

/** 用户列表行；不携带密码散列。 */
@Data
@AllArgsConstructor
public class AdminUserRow {

    private Long id;
    private String username;
    private String displayName;
    private UserStatus status;
    private String email;
    private String phone;
    private LocalDateTime createdAt;
}
