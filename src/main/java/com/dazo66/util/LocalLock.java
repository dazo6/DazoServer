package com.dazo66.util;

import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.TYPE})
//整个运行阶段都可见
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface LocalLock {

    String lockBeanName();

}
