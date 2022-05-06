package org.cboard.security;/*
package org.cboard.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

*/
/**
 * @author dazo66
 **//*


@Configuration
public class SecurityConfiguration extends WebSecurityConfigurerAdapter {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.authorizeRequests()
                .regexMatchers("/render.html")
                .authenticated()
                .and()
                .httpBasic();
    }


}
*/
