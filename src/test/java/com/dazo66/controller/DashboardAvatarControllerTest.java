package com.dazo66.controller;
import com.dazo66.security.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import static org.assertj.core.api.Assertions.*;
class DashboardAvatarControllerTest {
    UserTestSupport s;DashboardAvatarController controller;
    @BeforeEach void setup(){s=new UserTestSupport();controller=new DashboardAvatarController(s.users);}
    @AfterEach void done(){s.close();}
    @Test void uploadStoresBase64ButQueriesReturnOnlyBinaryImages() throws Exception {
        s.create("reader","read:D:/*");CurrentUser.set(s.users.context("reader",null));
        ByteArrayOutputStream b=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(100,150,BufferedImage.TYPE_INT_RGB),"png",b);
        controller.upload(new MockMultipartFile("file","a.png","image/png",b.toByteArray()));
        assertThat(s.users.avatarOf("reader")).startsWith("data:image/png;base64,");
        assertThat(s.users.me()).doesNotContainKeys("avatarBase64");
        assertThat(ImageIO.read(new ByteArrayInputStream(controller.avatar("reader").getBody())).getWidth()).isEqualTo(256);
        assertThat(controller.icon().getBody()).isEqualTo(controller.avatar("reader").getBody());
        CurrentUser.clear();assertThat(ImageIO.read(new ByteArrayInputStream(controller.icon().getBody()))).isNotNull();
    }
    @Test void removedResetAvatarEndpointReturnsMethodNotAllowed() throws Exception {
        org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build()
            .perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/auth/me/avatar"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isMethodNotAllowed());
    }
    @Test void rejectsSvgAndInvisibleAvatar() {
        assertThatThrownBy(()->controller.upload(new MockMultipartFile("file","a.svg","image/svg+xml","<svg/>".getBytes())))
            .isInstanceOf(IllegalArgumentException.class);
        s.create("a","read:D:/*");s.create("b","read:C:/*");CurrentUser.set(s.users.context("a",null));
        assertThatThrownBy(()->controller.avatar("b")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
}
