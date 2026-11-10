package org.csu.mypetstore.service;

import org.csu.mypetstore.domain.enums.UserRole;
import org.csu.mypetstore.domain.enums.UserStatus;
import org.csu.mypetstore.entity.StoreUser;
import org.csu.mypetstore.exception.BusinessException;
import org.csu.mypetstore.mapper.StoreUserMapper;
import org.csu.mypetstore.security.SessionUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private StoreUserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userMapper, passwordEncoder);
    }

    private StoreUser user(UserRole role, UserStatus status) {
        return StoreUser.builder()
                .id(1L)
                .username("alice")
                .displayName("林同学")
                .passwordHash("$2a$10$hash")
                .role(role)
                .status(status)
                .build();
    }

    @Test
    void logsInEnabledCustomerWithTrimmedUsername() {
        when(userMapper.selectByUsername("alice")).thenReturn(user(UserRole.CUSTOMER, UserStatus.ENABLED));
        when(passwordEncoder.matches("PetStore123!", "$2a$10$hash")).thenReturn(true);

        SessionUser sessionUser = authService.loginUser("  alice  ", "PetStore123!");

        assertThat(sessionUser.id()).isEqualTo(1L);
        assertThat(sessionUser.username()).isEqualTo("alice");
        assertThat(sessionUser.displayName()).isEqualTo("林同学");
        assertThat(sessionUser.role()).isEqualTo(UserRole.CUSTOMER);
    }

    @Test
    void rejectsWrongPasswordWithGenericMessage() {
        when(userMapper.selectByUsername("alice")).thenReturn(user(UserRole.CUSTOMER, UserStatus.ENABLED));
        when(passwordEncoder.matches("wrong", "$2a$10$hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.loginUser("alice", "wrong"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("账号或密码错误，或账号不可用。");
    }

    @Test
    void rejectsDisabledCustomerBeforeCheckingPassword() {
        when(userMapper.selectByUsername("alice")).thenReturn(user(UserRole.CUSTOMER, UserStatus.DISABLED));

        assertThatThrownBy(() -> authService.loginUser("alice", "PetStore123!"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("账号或密码错误，或账号不可用。");
        verify(passwordEncoder, never()).matches("PetStore123!", "$2a$10$hash");
    }

    @Test
    void customerCannotUseAdminLogin() {
        when(userMapper.selectByUsername("alice")).thenReturn(user(UserRole.CUSTOMER, UserStatus.ENABLED));

        assertThatThrownBy(() -> authService.loginAdmin("alice", "PetStore123!"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("账号或密码错误，或账号不可用。");
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void adminLoginOnlyAcceptsAdminRole() {
        when(userMapper.selectByUsername("admin")).thenReturn(StoreUser.builder()
                .id(4L)
                .username("admin")
                .displayName("商城管理员")
                .passwordHash("$2a$10$admin")
                .role(UserRole.ADMIN)
                .status(UserStatus.ENABLED)
                .build());
        when(passwordEncoder.matches("PetStore123!", "$2a$10$admin")).thenReturn(true);

        SessionUser sessionUser = authService.loginAdmin("admin", "PetStore123!");

        assertThat(sessionUser.role()).isEqualTo(UserRole.ADMIN);
        assertThat(sessionUser.displayName()).isEqualTo("商城管理员");
    }

    @Test
    void blankAndOverlongUsernamesFailWithoutDatabaseLookup() {
        assertThatThrownBy(() -> authService.loginUser("   ", "PetStore123!"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> authService.loginUser("a".repeat(51), "PetStore123!"))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(userMapper, passwordEncoder);
    }

    @Test
    void missingUserFailsWithGenericMessage() {
        when(userMapper.selectByUsername("nobody")).thenReturn(null);

        assertThatThrownBy(() -> authService.loginUser("nobody", "PetStore123!"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("账号或密码错误，或账号不可用。");
    }
}
