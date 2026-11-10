package org.csu.mypetstore.web;

import org.csu.mypetstore.domain.enums.UserRole;
import org.csu.mypetstore.exception.BusinessException;
import org.csu.mypetstore.security.AuthInterceptor;
import org.csu.mypetstore.security.SessionUser;
import org.csu.mypetstore.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService)).build();
    }

    @Test
    void loginPageKeepsSafeReturnUrlAndRejectsExternalOne() throws Exception {
        mockMvc.perform(get("/login").param("returnUrl", "/orders"))
                .andExpect(status().isOk())
                .andExpect(view().name("SignonForm"))
                .andExpect(model().attribute("returnUrl", "/orders"));

        MvcResult unsafe = mockMvc.perform(get("/login").param("returnUrl", "//evil.example/steal"))
                .andExpect(status().isOk())
                .andExpect(view().name("SignonForm"))
                .andReturn();
        assertThat(unsafe.getModelAndView().getModel().get("returnUrl")).isNull();
    }

    @Test
    void loginPageRedirectsAlreadyAuthenticatedUser() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthInterceptor.SESSION_USER,
                new SessionUser(1L, "alice", "林同学", UserRole.CUSTOMER));

        mockMvc.perform(get("/login").param("returnUrl", "/orders").session(session))
                .andExpect(redirectedUrl("/orders"));
    }

    @Test
    void successfulLoginReplacesSessionAndHonoursReturnUrl() throws Exception {
        when(authService.loginUser(" alice ", "PetStore123!"))
                .thenReturn(new SessionUser(1L, "alice", "林同学", UserRole.CUSTOMER));

        MvcResult result = mockMvc.perform(post("/login")
                        .param("username", " alice ")
                        .param("password", "PetStore123!")
                        .param("returnUrl", "/orders"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/orders"))
                .andReturn();

        assertThat(result.getRequest().getSession(false).getAttribute(AuthInterceptor.SESSION_USER))
                .isEqualTo(new SessionUser(1L, "alice", "林同学", UserRole.CUSTOMER));
    }

    @Test
    void failedLoginShowsGenericErrorAndKeepsUsername() throws Exception {
        when(authService.loginUser("alice", "bad"))
                .thenThrow(new BusinessException("账号或密码错误，或账号不可用。"));

        mockMvc.perform(post("/login")
                        .param("username", "alice")
                        .param("password", "bad")
                        .param("returnUrl", "/orders"))
                .andExpect(status().isOk())
                .andExpect(view().name("SignonForm"))
                .andExpect(model().attribute("error", "账号或密码错误，或账号不可用。"))
                .andExpect(model().attribute("username", "alice"))
                .andExpect(model().attribute("returnUrl", "/orders"));
    }

    @Test
    void logoutInvalidatesSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthInterceptor.SESSION_USER,
                new SessionUser(1L, "alice", "林同学", UserRole.CUSTOMER));

        mockMvc.perform(post("/logout").session(session))
                .andExpect(redirectedUrl("/main"));
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void safeReturnUrlOnlyAllowsLocalPaths() {
        assertThat(AuthController.safeReturnUrl(null)).isNull();
        assertThat(AuthController.safeReturnUrl("")).isNull();
        assertThat(AuthController.safeReturnUrl("https://evil.example")).isNull();
        assertThat(AuthController.safeReturnUrl("//evil.example")).isNull();
        assertThat(AuthController.safeReturnUrl("/orders\\evil")).isNull();
        assertThat(AuthController.safeReturnUrl("/login")).isNull();
        assertThat(AuthController.safeReturnUrl("/logout")).isNull();
        assertThat(AuthController.safeReturnUrl("/orders/1?x=1")).isEqualTo("/orders/1?x=1");
    }
}
