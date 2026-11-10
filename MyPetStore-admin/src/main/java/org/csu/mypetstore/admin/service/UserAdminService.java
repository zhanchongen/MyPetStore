package org.csu.mypetstore.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.csu.mypetstore.admin.domain.enums.UserRole;
import org.csu.mypetstore.admin.domain.enums.UserStatus;
import org.csu.mypetstore.admin.dto.AdminUserRow;
import org.csu.mypetstore.admin.dto.PageResult;
import org.csu.mypetstore.admin.entity.StoreUser;
import org.csu.mypetstore.admin.exception.BadRequestException;
import org.csu.mypetstore.admin.exception.NotFoundException;
import org.csu.mypetstore.admin.mapper.StoreUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/** P1 用户启停：只允许操作 CUSTOMER，禁止角色提升或管理员互禁。 */
@Service
public class UserAdminService {

    private final StoreUserMapper userMapper;

    public UserAdminService(StoreUserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public PageResult<AdminUserRow> search(String usernameRaw, String statusRaw, int page, int pageSize) {
        String username = usernameRaw == null ? null : usernameRaw.trim();
        if (username != null && username.length() > 50) {
            throw new BadRequestException("用户名关键词不能超过 50 个字符。");
        }
        UserStatus status = parseStatus(statusRaw);
        QueryWrapper<StoreUser> wrapper = new QueryWrapper<StoreUser>()
                .eq("role", UserRole.CUSTOMER.name());
        if (StringUtils.hasText(username)) {
            wrapper.like("username", username);
        }
        if (status != null) {
            wrapper.eq("status", status.name());
        }
        wrapper.orderByAsc("id");

        Page<StoreUser> result = userMapper.selectPage(new Page<>(page, pageSize), wrapper);
        List<AdminUserRow> rows = new ArrayList<>();
        for (StoreUser user : result.getRecords()) {
            rows.add(new AdminUserRow(
                    user.getId(),
                    user.getUsername(),
                    user.getDisplayName(),
                    user.getStatus(),
                    user.getEmail(),
                    user.getPhone(),
                    user.getCreatedAt()));
        }
        return new PageResult<>(rows, result.getTotal(), page, pageSize);
    }

    /** 返回 false 表示用户已经是目标状态，没有修改。 */
    @Transactional
    public boolean changeStatus(Long id, String targetRaw) {
        UserStatus target = parseTarget(targetRaw);
        StoreUser user = userMapper.selectById(id);
        if (user == null || user.getRole() != UserRole.CUSTOMER) {
            throw new NotFoundException("用户不存在或不是普通会员。");
        }
        if (user.getStatus() == target) {
            return false;
        }
        if (userMapper.updateCustomerStatus(id, target.name()) == 0) {
            throw new NotFoundException("用户不存在或不是普通会员。");
        }
        return true;
    }

    public UserStatus parseStatus(String value) {
        if (!StringUtils.hasText(value) || "ALL".equalsIgnoreCase(value)) {
            return null;
        }
        try {
            return UserStatus.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("未知的用户状态。");
        }
    }

    private UserStatus parseTarget(String value) {
        UserStatus status = parseStatus(value);
        if (status == null) {
            throw new BadRequestException("请选择启用或禁用状态。");
        }
        return status;
    }
}
