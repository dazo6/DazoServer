package org.cboard.util;

import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;
import org.springframework.security.crypto.password.MessageDigestPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * <p>
 * MD5 implementation of PasswordEncoder.
 * </p>
 * <p>
 * If a <code>null</code> password is presented, it will be treated as an empty
 * <code>String</code> ("") password.
 * </p>
 * <P>
 * As MD5 is a one-way hash, the salt can contain any characters.
 * </p>
 *
 * This is a convenience class that extends the {@link MessageDigestPasswordEncoder} and
 * passes MD5 as the algorithm to use.
 *
 * @author Ray Krueger
 * @author colin sampaleanu
 * @author Ben Alex
 */
public class Md5PasswordEncoder implements PasswordEncoder {

    @Override
    public String encode(CharSequence rawPassword) {
        String md5 = Hashing.md5().newHasher().putString(rawPassword, Charsets.UTF_8).hash().toString();
        return md5;
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        return encode(rawPassword).equals(encodedPassword);
    }
}

