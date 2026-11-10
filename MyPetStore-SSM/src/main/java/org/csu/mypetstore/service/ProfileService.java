package org.csu.mypetstore.service;

import org.csu.mypetstore.common.PasswordPolicy;
import org.csu.mypetstore.entity.StoreUser;
import org.csu.mypetstore.exception.BadRequestException;
import org.csu.mypetstore.exception.NotFoundException;
import org.csu.mypetstore.mapper.StoreUserMapper;
import org.csu.mypetstore.security.SessionUser;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {

    private final StoreUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public ProfileService(StoreUserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public StoreUser require(long userId) {
        StoreUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new NotFoundException("用户不存在。");
        }
        return user;
    }

    @Transactional
    public SessionUser updateProfile(long userId, String displayName, String email, String phone) {
        StoreUser user = require(userId);
        String normalizedName = displayName == null ? "" : displayName.trim();
        String normalizedEmail = email == null ? null : email.trim();
        String normalizedPhone = phone == null ? null : phone.trim();

        if (normalizedName.isEmpty() || normalizedName.length() > 50) {
            throw new BadRequestException("显示名称必须为 1—50 个字符。");
        }
        if (normalizedEmail != null && normalizedEmail.length() > 100) {
            throw new BadRequestException("邮箱最多 100 个字符。");
        }
        if (normalizedPhone != null && normalizedPhone.length() > 30) {
            throw new BadRequestException("电话最多 30 个字符。");
        }
        if (normalizedEmail != null && normalizedEmail.isEmpty()) {
            normalizedEmail = null;
        }
        if (normalizedPhone != null && normalizedPhone.isEmpty()) {
            normalizedPhone = null;
        }

        userMapper.updateProfile(user.getId(), normalizedName, normalizedEmail, normalizedPhone);
        return new SessionUser(user.getId(), user.getUsername(), normalizedName, user.getRole());
    }

    @Transactional
    public void changePassword(long userId, String oldPassword, String newPassword, String confirmPassword) {
        StoreUser user = require(userId);
        if (oldPassword == null || !passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new BadRequestException("旧密码不正确。");
        }
        if (newPassword == null || !newPassword.equals(confirmPassword)) {
            throw new BadRequestException("两次输入的新密码不一致。");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BadRequestException("新密码不能与旧密码相同。");
        }
        PasswordPolicy.validate(newPassword);
        userMapper.updatePassword(user.getId(), passwordEncoder.encode(newPassword));
    }
}
