package com.dazo66.security;
import org.junit.jupiter.api.*;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import static org.assertj.core.api.Assertions.*;
import java.util.*;
class AclAspectTest {
    public static class Handler {
        @ACL(type=AclType.UPDATE,point="op:resource.change") public void change() {}
        @ACL(type=AclType.READ) public void defaults() {}
        @ACL(type=AclType.READ,point="$arg1.field1") public void referenced(Dto dto) {}
        public void unannotated() {}
    }
    public static class Dto {
        private String field1;
        public Dto(String v){field1=v;}
        public String getField1(){return field1;}
    }
    Handler handler;
    @BeforeEach void setup() {
        AspectJProxyFactory factory=new AspectJProxyFactory(new Handler());factory.addAspect(new AclAspect());
        handler=factory.getProxy();
        CurrentUser.set(new CurrentUser("reader","reader",false,Arrays.asList("read:D:/*","read:op:"+Handler.class.getName()+".defaults"),null));
    }
    @AfterEach void done(){CurrentUser.clear();}
    @Test void deniesMutationAndAllowsUnannotatedAndDefaultPoint() {
        assertThatThrownBy(()->handler.change()).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        handler.unannotated();handler.defaults();
    }
    @Test void safelyRejectsNullOrWrongTypeReferences() {
        assertThatThrownBy(()->handler.referenced(new Dto(null))).isInstanceOf(IllegalArgumentException.class);
    }
}
