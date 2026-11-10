package org.csu.mypetstore.admin.service;

import org.csu.mypetstore.admin.domain.enums.UserRole;
import org.csu.mypetstore.admin.domain.enums.UserStatus;
import org.csu.mypetstore.admin.entity.StoreUser;
import org.csu.mypetstore.admin.mapper.StoreUserMapper;
import org.csu.mypetstore.admin.security.SessionUser;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;

/** 管理端登录：只允许 ENABLED 的 ADMIN，账号密码来自初始化数据。 */
@Service
public class AdminAuthService {

    private final StoreUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public AdminAuthService(StoreUserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public Optional<SessionUser> login(String username, String password) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            return Optional.empty();
        }
        StoreUser user = userMapper.selectByUsername(username.trim());
        if (user == null
                || user.getRole() != UserRole.ADMIN
                || user.getStatus() != UserStatus.ENABLED) {
            return Optional.empty();
        }
        String stored = user.getPasswordHash();
        if (stored == null || stored.isBlank()) {
            return Optional.empty();
        }
        try {
            if (!passwordEncoder.matches(password, stored)) {
                return Optional.empty();
            }
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
        return Optional.of(new SessionUser(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole()));
    }
}
