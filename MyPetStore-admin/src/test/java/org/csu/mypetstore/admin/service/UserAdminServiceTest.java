package org.csu.mypetstore.admin.service;

import org.csu.mypetstore.admin.domain.enums.UserRole;
import org.csu.mypetstore.admin.domain.enums.UserStatus;
import org.csu.mypetstore.admin.entity.StoreUser;
import org.csu.mypetstore.admin.exception.BadRequestException;
import org.csu.mypetstore.admin.exception.NotFoundException;
import org.csu.mypetstore.admin.mapper.StoreUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAdminServiceTest {

    @Mock
    private StoreUserMapper userMapper;

    private UserAdminService service;

    @BeforeEach
    void setUp() {
        service = new UserAdminService(userMapper);
    }

    private StoreUser user(UserRole role, UserStatus status) {
        return StoreUser.builder().id(2L).username("bob").role(role).status(status).build();
    }

    @Test
    void disablesCustomer() {
        when(userMapper.selectById(2L)).thenReturn(user(UserRole.CUSTOMER, UserStatus.ENABLED));
        when(userMapper.updateCustomerStatus(2L, "DISABLED")).thenReturn(1);

        assertThat(service.changeStatus(2L, "DISABLED")).isTrue();
        verify(userMapper).updateCustomerStatus(2L, "DISABLED");
    }

    @Test
    void repeatedStatusChangeIsNoOp() {
        when(userMapper.selectById(2L)).thenReturn(user(UserRole.CUSTOMER, UserStatus.DISABLED));

        assertThat(service.changeStatus(2L, "DISABLED")).isFalse();
        verify(userMapper, never()).updateCustomerStatus(2L, "DISABLED");
    }

    @Test
    void administratorCannotBeDisabled() {
        when(userMapper.selectById(4L)).thenReturn(StoreUser.builder()
                .id(4L)
                .role(UserRole.ADMIN)
                .status(UserStatus.ENABLED)
                .build());

        assertThatThrownBy(() -> service.changeStatus(4L, "DISABLED"))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("用户不存在或不是普通会员。");
    }

    @Test
    void invalidTargetStatusIsBadRequest() {
        assertThatThrownBy(() -> service.changeStatus(2L, "ALL"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("请选择启用或禁用状态。");
    }

    @Test
    void searchRejectsOverlongUsername() {
        assertThatThrownBy(() -> service.search("a".repeat(51), null, 1, 10))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("用户名关键词不能超过 50 个字符。");
    }
}
