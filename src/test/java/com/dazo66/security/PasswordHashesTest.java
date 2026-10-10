package com.dazo66.security;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class PasswordHashesTest {
    @Test void acceptsEightCharacterPasswordAndVerifiesIt() {
        String hash=PasswordHashes.hash("test1234");
        assertThat(PasswordHashes.verify("test1234",hash)).isTrue();
        assertThat(PasswordHashes.verify("test1235",hash)).isFalse();
    }
    @Test void rejectsShortAndOverlongPasswords() {
        assertThatThrownBy(()->PasswordHashes.hash("1234567")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("8");
        assertThatThrownBy(()->PasswordHashes.hash(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->PasswordHashes.hash(new String(new char[129]).replace('\0','x'))).isInstanceOf(IllegalArgumentException.class);
    }
}
