package eu.europeana.cloud.test;

import eu.europeana.cloud.service.mcs.MCSAppInitializer;
import eu.europeana.cloud.service.mcs.SecurityInitializer;
import eu.europeana.cloud.service.mcs.config.AuthorizationConfiguration;
import eu.europeana.cloud.service.mcs.config.ServiceConfiguration;
import eu.europeana.cloud.service.mcs.config.UnitedExceptionMapper;
import eu.europeana.cloud.service.mcs.utils.testcontexts.SecurityTestContext;
import eu.europeana.cloud.service.mcs.utils.testcontexts.TestAuthentificationConfiguration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.rules.SpringClassRule;
import org.springframework.test.context.junit4.rules.SpringMethodRule;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.servlet.http.HttpServletRequest;

import static eu.europeana.cloud.service.mcs.rest.AbstractResourceTest.mockHttpServletRequest;


/**
 * Helper class thats logs-in people to perform permission tests.
 */
//@ContextConfiguration(locations = {
//        "classpath:authentication-context-test.xml", // authentication uses a static InMemory list of usernames, passwords
//        "classpath:authorization-context-test.xml", // authorization uses Embedded cassandra
//		"classpath:aaTestContext.xml"
//        })
    @RunWith(CassandraTestRunner.class)
    @TestPropertySource(properties = {"numberOfElementsOnPage=100"})
    @WebAppConfiguration
    @ContextConfiguration(classes = {MCSAppInitializer.class,AuthorizationConfiguration.class, TestAuthentificationConfiguration.class,
            SecurityInitializer.class, ServiceConfiguration.class,
            UnitedExceptionMapper.class, SecurityTestContext.class})
    public abstract class AbstractSecurityTest {


        @Rule
        public SpringClassRule springRule = new SpringClassRule();

        @Rule
        public SpringMethodRule methodRule = new SpringMethodRule();

        @Autowired
        protected WebApplicationContext applicationContext;

        protected MockMvc mockMvc;

    protected HttpServletRequest URI_INFO; /****/

        @Before
        public void prepareMockMvc() throws Exception {
            mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                    .build();

           URI_INFO= mockHttpServletRequest();
        }



    protected String getBaseUri() {
            return "localhost:80/";
        }



    @Autowired
    private AuthenticationManager authenticationManager;

    @Before
    public synchronized void clear() {
        SecurityContextHolder.clearContext();
    }

    protected synchronized void login(String name, String password) {
        Authentication auth = new UsernamePasswordAuthenticationToken(name, password);
        SecurityContextHolder.getContext().setAuthentication(authenticationManager.authenticate(auth));
    }

    protected synchronized void logoutEveryone() {
        SecurityContextHolder.getContext().setAuthentication(null);
    }


}
