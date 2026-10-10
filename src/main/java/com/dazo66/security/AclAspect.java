package com.dazo66.security;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.beans.BeanWrapperImpl;
import java.lang.reflect.Method;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
@Aspect @Component
public class AclAspect {
    @Around("@annotation(com.dazo66.security.ACL) || @annotation(com.dazo66.security.ACLs) || @within(com.dazo66.security.ACL) || @within(com.dazo66.security.ACLs)")
    public Object enforce(ProceedingJoinPoint call) throws Throwable {
        Method m=((MethodSignature)call.getSignature()).getMethod();
        for(ACL acl:call.getTarget().getClass().getAnnotationsByType(ACL.class)) check(acl,m,call.getArgs());
        for(ACL acl:m.getAnnotationsByType(ACL.class)) check(acl,m,call.getArgs());
        return call.proceed();
    }
    private void check(ACL a,Method m,Object[] args) {
        String p=a.point();
        if(p.isEmpty()) p="op:"+m.getDeclaringClass().getName()+"."+m.getName();
        else if(p.startsWith("$")) {
            if(!p.matches("\\$arg[1-9][0-9]*(\\.[A-Za-z_][A-Za-z0-9_]*)*"))
                throw new IllegalArgumentException("ACL参数引用无效");
            String[] parts=p.substring(4).split("\\.",2);int wanted=Integer.parseInt(parts[0]),n=0;
            Object value=null;
            for(Object arg:args) {
                if(arg instanceof ServletRequest || arg instanceof ServletResponse) continue;
                if(++n==wanted) {value=arg;break;}
            }
            if(value==null) throw new IllegalArgumentException("ACL参数为空");
            if(parts.length>1) {
                if(parts[1].contains("class")) throw new IllegalArgumentException("ACL属性无效");
                value=new BeanWrapperImpl(value).getPropertyValue(parts[1]);
            }
            if(!(value instanceof String)) throw new IllegalArgumentException("ACL权限点必须是字符串");
            p=(String)value;
            if(!p.startsWith("op:") && !p.equals("*")) p=AclAccess.path(p);
        }
        AclAccess.check(a.type(),p);
    }
}
