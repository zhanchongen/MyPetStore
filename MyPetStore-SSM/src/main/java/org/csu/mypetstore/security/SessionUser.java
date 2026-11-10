package org.csu.mypetstore.security;

import org.csu.mypetstore.domain.enums.UserRole;

import java.io.Serializable;

/** Session 中只保存最小身份信息，权限每次请求重新查库。 */
public record SessionUser(Long id, String username, String displayName, UserRole role) implements Serializable {
}
