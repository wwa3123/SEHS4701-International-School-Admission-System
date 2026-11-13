package hk.edu.sehs4701.cissas.controller.web;

import hk.edu.sehs4701.cissas.entity.Member;
import hk.edu.sehs4701.cissas.dto.response.AcademicYearResponse;
import hk.edu.sehs4701.cissas.dto.response.ProgrammeLevelResponse;
import hk.edu.sehs4701.cissas.enums.AccountStatus;
import hk.edu.sehs4701.cissas.enums.AcademicYearStatus;
import hk.edu.sehs4701.cissas.enums.LevelCode;
import hk.edu.sehs4701.cissas.repository.MemberRepository;
import hk.edu.sehs4701.cissas.security.SecurityConfiguration;
import hk.edu.sehs4701.cissas.service.AdminAccountService;
import hk.edu.sehs4701.cissas.service.MembershipRegistrationService;
import hk.edu.sehs4701.cissas.service.OtcService;
import hk.edu.sehs4701.cissas.service.RateLimitService;
import hk.edu.sehs4701.cissas.service.AcademicYearService;
import hk.edu.sehs4701.cissas.service.ProgrammeLevelService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;

import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(MembershipPagesTest.TestConfiguration.class)
@WebAppConfiguration
class MembershipPagesTest {
    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import({MembershipPageController.class, AuthPageController.class, MemberPageController.class,
            PublicPageController.class, SecurityConfiguration.class, MemberSecurityProbe.class})
    static class TestConfiguration {
        @Bean MembershipRegistrationService registrations() { return mock(MembershipRegistrationService.class); }
        @Bean RateLimitService limits() { return mock(RateLimitService.class); }
        @Bean MemberRepository members() { return mock(MemberRepository.class); }
        @Bean AdminAccountService accounts() { return mock(AdminAccountService.class); }
        @Bean AcademicYearService years() { return mock(AcademicYearService.class); }
        @Bean ProgrammeLevelService levels() { return mock(ProgrammeLevelService.class); }
        @Bean ThymeleafViewResolver views(ApplicationContext context) {
            SpringResourceTemplateResolver templates = new SpringResourceTemplateResolver();
            templates.setApplicationContext(context);
            templates.setPrefix("classpath:/templates/");
            templates.setSuffix(".html");
            templates.setTemplateMode("HTML");
            SpringTemplateEngine engine = new SpringTemplateEngine();
            engine.setTemplateResolver(templates);
            ThymeleafViewResolver resolver = new ThymeleafViewResolver();
            resolver.setTemplateEngine(engine);
            resolver.setCharacterEncoding("UTF-8");
            return resolver;
        }
    }

    @RestController
    static class MemberSecurityProbe {
        @GetMapping("/member/registration-security-check")
        String identity(HttpServletRequest request) {
            return request.getUserPrincipal().getName() + ":" + request.isUserInRole("MEMBER");
        }
    }

    @Autowired private WebApplicationContext context;
    @Autowired private MembershipRegistrationService registrations;
    @Autowired private RateLimitService limits;
    @Autowired private MemberRepository members;
    @Autowired private AdminAccountService accounts;
    @Autowired private AcademicYearService years;
    @Autowired private ProgrammeLevelService levels;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        reset(registrations, limits, members, accounts, years, levels);
        when(limits.resolveClientAddress(any())).thenReturn("127.0.0.1");
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void mergedFrontendRoutesDelegateToExistingSsrWorkflows() throws Exception {
        mvc.perform(get("/register")).andExpect(redirectedUrl("/membership/register"));
        mvc.perform(get("/verify-email").param("email", "member@example.com"))
                .andExpect(redirectedUrl("/membership/verify-email?email=member%40example.com"));
        mvc.perform(get("/complete-registration")).andExpect(redirectedUrl("/membership/complete-registration"));

        mvc.perform(get("/apply")).andExpect(redirectedUrl("/login"));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("authenticatedUserId", 7L);
        session.setAttribute("authenticatedUserRole", "ROLE_MEMBER");
        Member member = new Member();
        member.setAccountStatus(AccountStatus.ACTIVE);
        when(members.findById(7L)).thenReturn(Optional.of(member));
        mvc.perform(get("/dashboard").session(session)).andExpect(redirectedUrl("/member/applications"));
        mvc.perform(get("/my-applications").session(session)).andExpect(redirectedUrl("/member/applications"));
        mvc.perform(get("/apply").session(session)).andExpect(redirectedUrl("/member/applications/new"));
        mvc.perform(get("/applications/APP-1").session(session))
                .andExpect(redirectedUrl("/member/applications/APP-1"));

        when(years.getCurrentOpenForApplicationAcademicYear()).thenThrow(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "No academic year open for applications found"));
        mvc.perform(get("/about")).andExpect(status().isOk());
        mvc.perform(get("/admission-info")).andExpect(status().isOk())
                .andExpect(content().string(containsString("There is currently no open admission year.")));
        var year = new AcademicYearResponse(1L, "2028/29", LocalDate.of(2028, 1, 1), null,
                AcademicYearStatus.ACTIVE);
        doReturn(year).when(years).getCurrentOpenForApplicationAcademicYear();
        when(levels.list(1L)).thenReturn(List.of(new ProgrammeLevelResponse(10L, 1L, LevelCode.S1,
                "Secondary 1", 20, LocalDate.of(2028, 9, 1), LocalDate.of(2028, 9, 30))));
        mvc.perform(get("/admission-info")).andExpect(status().isOk())
                .andExpect(content().string(containsString("2028/29")))
                .andExpect(content().string(containsString("Secondary 1")));
    }

    @Test
    void entireFlowRendersTemplatesAcceptsActualCsrfAndConsumesSessionProof() throws Exception {
        var page = mvc.perform(get("/membership/register")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Send verification code"))).andReturn();
        MockHttpSession session = (MockHttpSession) page.getRequest().getSession(false);
        mvc.perform(post("/membership/register").session(session).param("_csrf", hidden(page.getResponse().getContentAsString(), "_csrf"))
                        .param("email", "MEMBER@example.com"))
                .andExpect(redirectedUrl("/membership/verify-email?email=MEMBER%40example.com"))
                .andExpect(flash().attribute("message", accepted()));
        var verification = mvc.perform(get("/membership/verify-email").session(session).param("email", "MEMBER@example.com"))
                .andExpect(status().isOk()).andExpect(unauthenticated())
                .andExpect(content().string(containsString("Resend verification code"))).andReturn();
        String oldId = session.getId();
        mvc.perform(post("/membership/verify-email").session(session)
                        .param("_csrf", hidden(verification.getResponse().getContentAsString(), "_csrf"))
                        .param("email", "MEMBER@example.com").param("code", "123456")
                        .param("authenticatedUserRole", "ROLE_SUPER_ADMIN"))
                .andExpect(redirectedUrl("/membership/complete-registration")).andExpect(unauthenticated());
        assertNotEquals(oldId, session.getId());
        assertEquals("member@example.com", session.getAttribute("registrationVerifiedEmail"));
        assertNull(session.getAttribute("authenticatedUserId"));
        assertNull(session.getAttribute("authenticatedUserRole"));
        mvc.perform(get("/membership/complete-registration").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("member@example.com")))
                .andExpect(content().string(containsString("PASSPORT")));
        mvc.perform(details(session)).andExpect(redirectedUrl("/membership/success"))
                .andExpect(flash().attributeExists("message"));
        assertNull(session.getAttribute("registrationVerifiedEmail"));
        assertNull(session.getAttribute("registrationVerifiedUntil"));
        mvc.perform(details(session)).andExpect(status().isForbidden());
        mvc.perform(get("/membership/success")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Sign in")));
        verify(registrations).completeRegistration(eq("member@example.com"),
                argThat(form -> form.firstName().equals("Test") && form.password().equals("secure-password")));
        verify(limits).checkAllowed("127.0.0.1");
        verify(limits).checkAllowed("registration-verify:127.0.0.1");
    }

    @Test
    void verificationRotatesSessionWithoutChangingExistingAuthenticatedRole() throws Exception {
        Member member = new Member();
        member.setAccountStatus(AccountStatus.ACTIVE);
        when(members.findById(7L)).thenReturn(Optional.of(member));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("authenticatedUserId", 7L);
        session.setAttribute("authenticatedUserRole", "ROLE_MEMBER");
        String oldId = session.getId();
        verifyEmail(session);
        assertNotEquals(oldId, session.getId());
        assertEquals(7L, session.getAttribute("authenticatedUserId"));
        assertEquals("ROLE_MEMBER", session.getAttribute("authenticatedUserRole"));
        mvc.perform(get("/membership/complete-registration").session(session))
                .andExpect(status().isOk());
        mvc.perform(get("/member/registration-security-check").session(session))
                .andExpect(status().isOk()).andExpect(content().string("7:true"));
        mvc.perform(get("/member/registration-security-check")).andExpect(redirectedUrl("/login"));
        verify(members, atLeast(3)).findById(7L);
        mvc.perform(get("/super-admin/admissions-officers").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/membership/verify-email")).andExpect(status().isOk()).andExpect(unauthenticated());
    }

    @Test
    void registerConflictsDuplicateRacesAndCooldownAllReturnGenericRedirect() throws Exception {
        for (RuntimeException failure : new RuntimeException[]{
                duplicate(), new OtcService.ResendCooldownException("Please wait"),
                new ResponseStatusException(HttpStatus.CONFLICT, "Already registered")}) {
            doThrow(failure).when(registrations).register(any());
            mvc.perform(post("/membership/register").with(csrf()).param("email", "member@example.com"))
                    .andExpect(status().is3xxRedirection()).andExpect(flash().attribute("message", accepted()));
        }
    }

    @Test
    void nonDuplicateIntegrityErrorsAreNotHidden() {
        var failure = new DataIntegrityViolationException("not null", new SQLException("not null", "23000", 1048));
        doThrow(failure).when(registrations).register(any());
        var thrown = assertThrows(Exception.class,
                () -> mvc.perform(post("/membership/register").with(csrf()).param("email", "member@example.com")));
        assertSame(failure, thrown.getCause());
    }

    @Test
    void emailAndVerificationValidationStayOnRenderedForms() throws Exception {
        mvc.perform(post("/membership/register").with(csrf()).param("email", "invalid"))
                .andExpect(status().isBadRequest()).andExpect(view().name("auth/registration"))
                .andExpect(model().attributeHasFieldErrors("emailForm", "email"));
        mvc.perform(post("/membership/verify-email").with(csrf()).param("email", "member@example.com").param("code", "abc"))
                .andExpect(status().isBadRequest()).andExpect(view().name("auth/registration-verify"));
        mvc.perform(post("/membership/resend-verification").with(csrf()).param("email", "invalid"))
                .andExpect(status().isBadRequest()).andExpect(view().name("auth/registration-verify"));
        verifyNoInteractions(registrations);
    }

    @Test
    void failedOrInvalidVerificationRemovesPreviousProofAndDoesNotEchoCode() throws Exception {
        MockHttpSession session = new MockHttpSession();
        verifyEmail(session);
        doThrow(new IllegalArgumentException("Account details must not leak")).when(registrations).verifyEmail(anyString(), anyString());
        mvc.perform(post("/membership/verify-email").session(session).with(csrf())
                        .param("email", "member@example.com").param("code", "987654"))
                .andExpect(status().isBadRequest()).andExpect(content().string(containsString("Unable to verify membership")))
                .andExpect(content().string(not(containsString("987654"))))
                .andExpect(content().string(not(containsString("Account details must not leak"))));
        assertNull(session.getAttribute("registrationVerifiedEmail"));
        doNothing().when(registrations).verifyEmail(anyString(), anyString());
        verifyEmail(session);
        mvc.perform(post("/membership/verify-email").session(session).with(csrf())
                        .param("email", "member@example.com").param("code", "invalid"))
                .andExpect(status().isBadRequest());
        assertNull(session.getAttribute("registrationVerifiedEmail"));
    }

    @Test
    void proofIsSessionBoundExpiresAndCannotBeReplayed() throws Exception {
        MockHttpSession verified = new MockHttpSession();
        verifyEmail(verified);
        mvc.perform(details(new MockHttpSession())).andExpect(status().isForbidden())
                .andExpect(view().name("auth/registration-verify"));
        verified.setAttribute("registrationVerifiedUntil", Instant.now().minusSeconds(1));
        mvc.perform(details(verified)).andExpect(status().isForbidden());
        assertNull(verified.getAttribute("registrationVerifiedEmail"));
        mvc.perform(get("/membership/complete-registration").session(verified))
                .andExpect(redirectedUrl("/membership/verify-email")).andExpect(flash().attributeExists("error"));
        verify(registrations, never()).completeRegistration(any(), any());
    }

    @Test
    void duplicateIdentityRetainsProofForRetryAndNeverEchoesPassword() throws Exception {
        MockHttpSession session = new MockHttpSession();
        verifyEmail(session);
        doThrow(duplicate()).doNothing().when(registrations).completeRegistration(anyString(), any());
        mvc.perform(details(session)).andExpect(status().isConflict()).andExpect(view().name("auth/registration-details"))
                .andExpect(content().string(containsString("Identity document is already linked")))
                .andExpect(content().string(not(containsString("secure-password"))));
        assertNotNull(session.getAttribute("registrationVerifiedEmail"));
        mvc.perform(details(session)).andExpect(redirectedUrl("/membership/success"));
    }

    @Test
    void completionHandlesBindingAndServiceValidationWithoutLosingProof() throws Exception {
        MockHttpSession session = new MockHttpSession();
        verifyEmail(session);
        mvc.perform(details(session).with(request -> { request.setParameter("documentType", "NOT_A_TYPE"); return request; }))
                .andExpect(status().isBadRequest()).andExpect(view().name("auth/registration-details"))
                .andExpect(model().attributeHasFieldErrors("registrationForm", "documentType"))
                .andExpect(content().string(not(containsString("secure-password"))));
        verify(registrations, never()).completeRegistration(any(), any());
        for (HttpStatus status : new HttpStatus[]{HttpStatus.BAD_REQUEST, HttpStatus.FORBIDDEN, HttpStatus.CONFLICT}) {
            doThrow(new ResponseStatusException(status, "Check your details"))
                    .when(registrations).completeRegistration(anyString(), any());
            mvc.perform(details(session)).andExpect(status().is(status.value()))
                    .andExpect(content().string(containsString("Check your details")));
            assertNotNull(session.getAttribute("registrationVerifiedEmail"));
        }
    }

    @Test
    void serviceReceivesSessionEmailNotTamperedEmail() throws Exception {
        MockHttpSession session = new MockHttpSession();
        verifyEmail(session);
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Email verification is required"))
                .when(registrations).completeRegistration(eq("member@example.com"), any());
        mvc.perform(details(session).with(request -> { request.setParameter("email", "other@example.com"); return request; }))
                .andExpect(status().isForbidden());
        verify(registrations).completeRegistration(eq("member@example.com"),
                argThat(form -> form.email().equals("other@example.com")));
    }

    @Test
    void resendUsesSameGenericMessageWithOrWithoutCooldownAndPreservesRateKey() throws Exception {
        mvc.perform(post("/membership/resend-verification").with(csrf()).param("email", "unknown@example.com"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attribute("message", accepted()));
        doThrow(new OtcService.ResendCooldownException("Please wait")).when(registrations).resendVerification(anyString());
        mvc.perform(post("/membership/resend-verification").with(csrf()).param("email", "member@example.com"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attribute("message", accepted()));
        verify(limits, times(2)).checkAllowed("registration-resend:127.0.0.1");
    }

    @Test
    void rateLimitsRenderEachFormAndDoNotCallService() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many registration requests"))
                .when(limits).checkAllowed(anyString());
        mvc.perform(post("/membership/register").with(csrf()).param("email", "member@example.com"))
                .andExpect(status().isTooManyRequests()).andExpect(view().name("auth/registration"));
        mvc.perform(post("/membership/verify-email").with(csrf()).param("email", "member@example.com").param("code", "123456"))
                .andExpect(status().isTooManyRequests()).andExpect(view().name("auth/registration-verify"));
        mvc.perform(post("/membership/resend-verification").with(csrf()).param("email", "member@example.com"))
                .andExpect(status().isTooManyRequests()).andExpect(view().name("auth/registration-verify"));
        verifyNoInteractions(registrations);
    }

    @Test
    void allMutationsRequireCsrf() throws Exception {
        for (String route : new String[]{"/membership/register", "/membership/verify-email",
                "/membership/resend-verification", "/membership/complete-registration"}) {
            mvc.perform(post(route)).andExpect(status().isForbidden());
        }
        verifyNoInteractions(registrations);
    }

    private void verifyEmail(MockHttpSession session) throws Exception {
        mvc.perform(post("/membership/verify-email").session(session).with(csrf())
                        .param("email", "MEMBER@example.com").param("code", "123456"))
                .andExpect(redirectedUrl("/membership/complete-registration"));
    }

    private MockHttpServletRequestBuilder details(MockHttpSession session) {
        return post("/membership/complete-registration").session(session).with(csrf())
                .param("firstName", "Test").param("lastName", "Member").param("email", "member@example.com")
                .param("phoneNumber", "12345678").param("documentType", "HKID")
                .param("documentNumber", "A123456(3)").param("password", "secure-password");
    }

    private DataIntegrityViolationException duplicate() {
        return new DataIntegrityViolationException("duplicate", new SQLException("duplicate", "23000", 1062));
    }

    private String accepted() {
        return "If this email can be registered, verification instructions will be sent";
    }

    private String hidden(String html, String name) {
        var matcher = Pattern.compile("name=\"" + name + "\"[^>]*value=\"([^\"]+)\"").matcher(html);
        assertTrue(matcher.find(), "Missing hidden field " + name);
        return matcher.group(1);
    }
}
