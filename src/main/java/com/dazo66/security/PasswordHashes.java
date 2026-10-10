package com.dazo66.security;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.*;
import java.util.*;
public final class PasswordHashes {
    private static final int ITERATIONS=600000;
    public static String hash(String password) {
        if(password==null || password.length()<8 || password.length()>128)
            throw new IllegalArgumentException("密码长度需要8至128个字符");
        byte[] salt=new byte[16];new SecureRandom().nextBytes(salt);
        return "pbkdf2-sha256$"+ITERATIONS+"$"+Base64.getEncoder().encodeToString(salt)+"$"+
            Base64.getEncoder().encodeToString(derive(password,salt,ITERATIONS));
    }
    public static boolean verify(String password,String encoded) {
        if(password==null || password.length()>128 || encoded==null) return false;
        try {
            String[] p=encoded.split("\\$");
            if(p.length!=4 || !p[0].equals("pbkdf2-sha256")) return false;
            return MessageDigest.isEqual(Base64.getDecoder().decode(p[3]),
                derive(password,Base64.getDecoder().decode(p[2]),Integer.parseInt(p[1])));
        } catch(RuntimeException e) {return false;}
    }
    private static byte[] derive(String p,byte[] salt,int count) {
        PBEKeySpec spec=new PBEKeySpec(p.toCharArray(),salt,count,256);
        try {return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}
        catch(GeneralSecurityException e) {throw new IllegalStateException(e);}
        finally {spec.clearPassword();}
    }
}
