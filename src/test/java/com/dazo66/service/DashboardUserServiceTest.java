package com.dazo66.service;
import com.dazo66.security.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class DashboardUserServiceTest {
    UserTestSupport s;
    @BeforeEach void setup(){s=new UserTestSupport();}
    @AfterEach void done(){s.close();}
    @Test void permissionSupersetControlsManagementWithoutHierarchy() {
        s.create("manager","read:D:/*","update:op:user.*");
        s.create("small","read:D:/test/*");
        s.create("outside","read:C:/test/*");
        s.create("peer","read:D:/*","update:op:user.*");
        CurrentUser.set(s.users.context("manager",null));
        assertThat(s.users.manageable(s.users.find("small"))).isTrue();
        assertThat(s.users.manageable(s.users.find("outside"))).isFalse();
        assertThat(s.users.manageable(s.users.find("peer"))).isFalse();
        assertThat(s.users.manageable(s.users.find("manager"))).isFalse();
        assertThatThrownBy(()->s.users.update("outside","profile",Collections.singletonMap("username","bad")))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        Map<String,Object> b=new HashMap<>();b.put("permissions",Arrays.asList("read:C:/bad"));
        assertThatThrownBy(()->s.users.requestPermissions(b,true)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
    @Test void permissionSpellingSurvivesCreateUpdateReadAndSessionContext() throws Exception {
        String original="read:D:\\OneDrive\\MixedCase\\*";
        s.create("reader",original);
        assertThat(s.users.detail("reader").get("permissions")).isEqualTo(Collections.singletonList(original));
        assertThat(new com.fasterxml.jackson.databind.ObjectMapper().readValue(s.jdbc.queryForObject("SELECT permissions FROM t_dashboard_user WHERE user_id='reader'",String.class),java.util.List.class)).containsExactly(original);
        String changed="read:D:/OneDrive/AnotherCase/*";
        s.users.update("reader","permissions",Collections.singletonMap("permissionText",changed));
        CurrentUser.set(s.users.context("reader",null));
        assertThat(s.users.me().get("permissions")).isEqualTo(Collections.singletonList(changed));
        assertThat(CurrentUser.get().permissions).containsExactly(changed);
        assertThat(AclAccess.can(AclType.READ,"d:\\onedrive\\anothercase\\a.png")).isTrue();
    }
    @Test void defaultsNamesAndNoDtoContainsAvatarOrHash() {
        Map<String,Object> u=s.create("reader","read:D:/test/*");
        assertThat(u.get("username")).isEqualTo("reader");
        assertThat(u).doesNotContainKeys("avatarBase64","avatar_base64","passwordHash","password_hash");
        CurrentUser.set(s.users.context("reader",null));s.users.avatar("data:image/png;base64,AAAA");
        assertThat(s.users.me()).doesNotContainKeys("avatarBase64","avatar_base64");
        s.root();assertThat(s.users.detail("reader")).doesNotContainKeys("avatarBase64","avatar_base64");
        assertThat(s.users.avatarOf("reader")).isNotNull();
    }
    @Test void requestUsesStartPermissionsAndOtherAccountsRemainIndependent() {
        s.create("manager","read:D:/*","update:op:user.*");s.create("child","read:D:/test/*");
        CurrentUser original=s.users.context("manager",null);
        s.root();s.users.update("manager","permissions",Collections.singletonMap("permissions",Collections.emptyList()));
        CurrentUser.set(original);
        assertThat(AclAccess.can(AclType.READ,"D:/test/a")).isTrue();
        assertThat(s.users.context("manager",null).permissions).isEmpty();
        assertThat(s.users.find("child").get("permissions")).isEqualTo(Collections.singletonList("read:D:/test/*"));
    }
}
