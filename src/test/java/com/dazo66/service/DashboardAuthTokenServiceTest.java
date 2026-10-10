package com.dazo66.service;
import com.dazo66.security.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class DashboardAuthTokenServiceTest {
    UserTestSupport s;
    @BeforeEach void setup(){s=new UserTestSupport();}
    @AfterEach void done(){s.close();}
    @Test void storesRandomSaltAndRejectsTamperingAndDeletedSessions() {
        String a=s.tokens.createToken("admin"),b=s.tokens.createToken("admin");
        assertThat(a).isNotEqualTo(b);assertThat(s.tokens.isValid(a)).isTrue();
        assertThat(s.jdbc.queryForList("SELECT token_salt FROM t_dashboard_user_session")).hasSize(2);
        assertThat(s.tokens.isValid(a.substring(0,a.length()-2)+"xx")).isFalse();
        s.tokens.logout(a.split("\\.")[1]);
        assertThat(s.tokens.isValid(a)).isFalse();assertThat(s.tokens.isValid(b)).isTrue();
        assertThat(s.tokens.isValid("old-token")).isFalse();
    }
    @Test void sameSessionReadsNewPermissionsAndDisabledUserCannotReturnWithOldToken() {
        s.create("reader","read:D:/test/*");
        String token=s.tokens.createToken("reader");
        assertThat(s.tokens.resolve(token).permissions).containsExactly("read:D:/test/*");
        s.root();s.users.update("reader","permissions",Collections.singletonMap("permissions",Collections.singletonList("read:C:/test/*")));
        assertThat(s.tokens.resolve(token).permissions).containsExactly("read:C:/test/*");
        s.users.update("reader","enabled",Collections.singletonMap("enabled",false));
        assertThat(s.tokens.resolve(token)).isNull();
        s.users.update("reader","enabled",Collections.singletonMap("enabled",true));
        assertThat(s.tokens.resolve(token)).isNull();
    }
    @Test void passwordsAreHashedAndChangingPasswordKeepsSessions() {
        s.create("reader","read:D:/test/*");String token=s.tokens.createToken("reader");
        assertThat(s.users.authenticate("reader","a-long-test-password")).isTrue();
        assertThat(s.users.authenticate("reader","wrong")).isFalse();
        s.users.update("reader","password",Collections.singletonMap("newPassword","a-new-long-password"));
        assertThat(s.users.authenticate("reader","a-long-test-password")).isFalse();
        assertThat(s.users.authenticate("reader","a-new-long-password")).isTrue();
        assertThat(s.tokens.isValid(token)).isTrue();
        assertThat(s.jdbc.queryForObject("SELECT password_hash FROM t_dashboard_user WHERE user_id='reader'",String.class)).startsWith("pbkdf2-sha256$");
    }
}
