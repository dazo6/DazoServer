package com.dazo66.controller;
import com.dazo66.service.ScheduledTaskRegistry;
import com.dazo66.security.CurrentUser;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class ScheduledTaskControllerTest {
    @AfterEach void clear(){CurrentUser.clear();}
    @Test void onlySuperAdminCanListOrRunEvenWithWildcardPermissions() throws Exception {
        ScheduledTaskRegistry registry=mock(ScheduledTaskRegistry.class);
        MockMvc mvc=MockMvcBuilders.standaloneSetup(new ScheduledTaskController(registry)).build();
        mvc.perform(get("/api/scheduled-tasks")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/scheduled-tasks/id/run")).andExpect(status().isUnauthorized());
        CurrentUser.set(new CurrentUser("admin","admin",false,Arrays.asList("read:*","update:*","delete:*"),null));
        mvc.perform(get("/api/scheduled-tasks")).andExpect(status().isForbidden());
        mvc.perform(post("/api/scheduled-tasks/id/run")).andExpect(status().isForbidden());
        verifyNoInteractions(registry);
        CurrentUser.set(new CurrentUser("root","root",true,Collections.emptyList(),null));
        when(registry.list()).thenReturn(Collections.emptyList());
        mvc.perform(get("/api/scheduled-tasks")).andExpect(status().isOk()).andExpect(jsonPath("$.data").isArray());
        mvc.perform(post("/api/scheduled-tasks/id/run")).andExpect(status().isAccepted());
        verify(registry).runManually("id");
    }
}
