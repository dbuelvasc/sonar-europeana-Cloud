package eu.europeana.cloud.service.mcs.config;

import eu.europeana.cloud.cassandra.CassandraConnectionProvider;
import eu.europeana.cloud.service.aas.authentication.CassandraAuthenticationService;
import eu.europeana.cloud.service.aas.authentication.handlers.CloudAuthenticationEntryPoint;
import eu.europeana.cloud.service.aas.authentication.repository.CassandraUserDAO;
import eu.europeana.cloud.service.commons.utils.PasswordEncoderFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.event.LoggerListener;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;

@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true, securedEnabled = true, proxyTargetClass = true)
//<expression-handler ref="expressionHandler" /> ??
public class AuthenticationConfiguration extends WebSecurityConfigurerAdapter {

  @Override
  protected void configure(HttpSecurity http) throws Exception {
    http.httpBasic()
        .authenticationEntryPoint(cloudAuthenticationEntryPoint())
        .and()
        .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        .and()
        .csrf().disable();
  }

  @Override
  protected void configure(AuthenticationManagerBuilder auth) throws Exception {
    auth.userDetailsService(authenticationService())
        .passwordEncoder(PasswordEncoderFactory.getPasswordEncoder());
  }

  @Bean
  public CloudAuthenticationEntryPoint cloudAuthenticationEntryPoint() {
    return new CloudAuthenticationEntryPoint();
  }

  @Bean
  public LoggerListener loggerListener() {
    return new LoggerListener();
  }

  /* ========= AUTHENTICATION STORAGE (USERNAME + PASSWORD TABLES IN CASSANDRA) ========= */

  @Bean
  public CassandraUserDAO userDAO(CassandraConnectionProvider aasCassandraProvider) {
    return new CassandraUserDAO(aasCassandraProvider);
  }

  @Bean
  public CassandraAuthenticationService authenticationService() {
    return new CassandraAuthenticationService();
  }

}
