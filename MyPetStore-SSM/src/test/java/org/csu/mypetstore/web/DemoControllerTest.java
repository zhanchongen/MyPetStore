package org.csu.mypetstore.web;

import org.csu.mypetstore.domain.enums.UserRole;
import org.csu.mypetstore.domain.enums.UserStatus;
import org.csu.mypetstore.entity.StoreUser;
import org.csu.mypetstore.mapper.StoreUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DemoControllerTest {

    @Mock
    private StoreUserMapper userMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new DemoController(userMapper)).build();
    }

    @Test
    void demoReturnsUtf8Text() throws Exception {
        mockMvc.perform(get("/demo"))
                .andExpect(status().isOk())
                .andExpect(content().string("Spring MVC 初始化成功！"));
    }

    @Test
    void userReturnsOnlyDisplayFields() throws Exception {
        when(userMapper.selectByUsername("alice")).thenReturn(StoreUser.builder()
                .id(1L)
                .username("alice")
                .passwordHash("$2a$10$never-return-this")
                .displayName("林同学")
                .role(UserRole.CUSTOMER)
                .status(UserStatus.ENABLED)
                .build());

        mockMvc.perform(get("/demo/user").param("username", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.displayName").value("林同学"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.status").value("ENABLED"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void unknownUserReturns404() throws Exception {
        mockMvc.perform(get("/demo/user").param("username", "nobody"))
                .andExpect(status().isNotFound());
    }
}
