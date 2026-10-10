package com.dazo66.security;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class GlobPermissionsTest {
    private List<String> r(String...r) {return GlobPermissions.normalize(Arrays.asList(r));}
    @Test void distinguishesTypesAndDriveBoundaries() {
        assertThat(GlobPermissions.subset(r("read:D:/test/*"),r("read:D:/*"))).isTrue();
        assertThat(GlobPermissions.subset(r("read:C:/test"),r("read:D:/*"))).isFalse();
        assertThat(GlobPermissions.subset(r("update:D:/test/*"),r("read:D:/*"))).isFalse();
        assertThat(GlobPermissions.matches("D:/test/*","d:\\test\\sub\\a.png")).isTrue();
        assertThat(GlobPermissions.matches("D:/test/*","D:/test2/a.png")).isFalse();
        assertThat(GlobPermissions.matches("D:/test/*","D:/test")).isFalse();
    }
    @Test void computesLanguageContainmentRatherThanMatchingPatternText() {
        assertThat(GlobPermissions.subset(r("read:D:/*a*"),r("read:D:/*a"))).isFalse();
        assertThat(GlobPermissions.subset(r("read:D:/*a"),r("read:D:/*a*"))).isTrue();
        assertThat(GlobPermissions.strictSubset(r("read:D:/a*"),r("read:D:/a*"))).isFalse();
        assertThat(GlobPermissions.strictSubset(r("read:D:/a*"),r("read:*"))).isTrue();
        assertThat(GlobPermissions.subset(r("read:op:user.*"),r("read:op:*"))).isTrue();
        assertThat(GlobPermissions.subset(r("read:op:user.*"),r("read:D:/*"))).isFalse();
        assertThat(GlobPermissions.subset(r("read:D:/a","read:D:/b"),r("read:D:/a","read:D:/b"))).isTrue();
    }
    @Test void preservesPointsWhileMatchingAndContainmentNormalizeThem() {
        String point="read:D:\\OneDrive\\MixedCase\\*";
        assertThat(r(point,point)).containsExactly(point);
        assertThat(GlobPermissions.allows(r(point),AclType.READ,"d:/onedrive/mixedcase/image.png")).isTrue();
        assertThat(GlobPermissions.subset(r(point),r("read:d:/onedrive/*"))).isTrue();
        assertThat(GlobPermissions.strictSubset(r(point),r("read:d:/onedrive/mixedcase/*"))).isFalse();
        assertThat(r("read:D:/MixedCase/")).containsExactly("read:D:/MixedCase/");
        assertThat(GlobPermissions.matches("op:User.Read","op:user.read")).isFalse();
    }
    @Test void rejectsInvalidRulesAndDeduplicates() {
        assertThat(r("READ:D:\\test\\*","read:d:/test/*")).containsExactly("read:D:\\test\\*","read:d:/test/*");
        assertThatThrownBy(()->r("read:D:/a/../*")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->r("execute:*")).isInstanceOf(IllegalArgumentException.class);
    }
}
