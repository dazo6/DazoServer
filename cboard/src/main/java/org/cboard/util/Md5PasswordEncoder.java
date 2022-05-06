package org.cboard.util;

import org.springframework.security.crypto.password.MessageDigestPasswordEncoder;

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
public class Md5PasswordEncoder extends MessageDigestPasswordEncoder {

    public Md5PasswordEncoder() {
        super("MD5");
    }
}

