package com.dazo66.security;
import java.lang.annotation.*;
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(ACLs.class)
public @interface ACL {
    AclType type();
    String point() default "";
}
