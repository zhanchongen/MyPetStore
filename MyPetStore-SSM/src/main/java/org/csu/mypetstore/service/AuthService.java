package org.csu.mypetstore.service;

import org.csu.mypetstore.domain.enums.UserRole;
import org.csu.mypetstore.entity.StoreUser;
import org.csu.mypetstore.exception.BusinessException;
import org.csu.mypetstore.mapper.StoreUserMapper;
import org.csu.mypetstore.security.SessionUser;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final String GENERIC_LOGIN_ERROR = "账号或密码错误，或账号不可用。";

    private final StoreUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public AuthService(StoreUserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public SessionUser loginUser(String username, String password) {
        return login(username, password, UserRole.CUSTOMER);
    }

    public SessionUser loginAdmin(String username, String password) {
        return login(username, password, UserRole.ADMIN);
    }

    private SessionUser login(String username, String password, UserRole expectedRole) {
        String normalized = username == null ? "" : username.trim();
        if (normalized.isEmpty() || normalized.length() > 50 || password == null || password.isEmpty()) {
            throw new BusinessException(GENERIC_LOGIN_ERROR);
        }

        StoreUser user = userMapper.selectByUsername(normalized);
        if (user == null
                || user.getRole() != expectedRole
                || !user.getStatus().isEnabled()
                || user.getPasswordHash() == null
                || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException(GENERIC_LOGIN_ERROR);
        }
        return new SessionUser(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole());
    }
}
