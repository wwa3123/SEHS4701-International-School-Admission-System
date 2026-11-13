package hk.edu.sehs4701.cissas.controller.web;

import hk.edu.sehs4701.cissas.dto.internal.LoginResult;
import hk.edu.sehs4701.cissas.dto.request.OfferDecisionRequest;
import hk.edu.sehs4701.cissas.dto.response.*;
import hk.edu.sehs4701.cissas.entity.Member;
import hk.edu.sehs4701.cissas.enums.*;
import hk.edu.sehs4701.cissas.repository.MemberRepository;
import hk.edu.sehs4701.cissas.security.SecurityConfiguration;
import hk.edu.sehs4701.cissas.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(MemberPagesTest.TestConfiguration.class)
@WebAppConfiguration
class MemberPagesTest {
    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfiguration.class, LoginPageController.class, MemberApplicationPageController.class,
            LoginSessionService.class,
            MemberSessionService.class})
    static class TestConfiguration {
        @Bean LoginService login() { return mock(LoginService.class); }
        @Bean RateLimitService rateLimits() { return mock(RateLimitService.class); }
        @Bean MemberRepository members() { return mock(MemberRepository.class); }
        @Bean AdminAccountService accounts() { return mock(AdminAccountService.class); }
        @Bean AdmissionsApplicationService admissions() { return mock(AdmissionsApplicationService.class); }
        @Bean ApplicationResultService results() { return mock(ApplicationResultService.class); }
        @Bean AcademicYearService years() { return mock(AcademicYearService.class); }
        @Bean ProgrammeLevelService levels() { return mock(ProgrammeLevelService.class); }

        @Bean
        ThymeleafViewResolver views(ApplicationContext context) {
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

    @Autowired private WebApplicationContext context;
    @Autowired private LoginService login;
    @Autowired private RateLimitService rateLimits;
    @Autowired private MemberRepository members;
    @Autowired private AdminAccountService accounts;
    @Autowired private AdmissionsApplicationService admissions;
    @Autowired private ApplicationResultService results;
    @Autowired private AcademicYearService years;
    @Autowired private ProgrammeLevelService levels;
    private MockMvc mvc;
    private MockHttpSession session;

    @BeforeEach
    void setup() {
        reset(login, rateLimits, members, accounts, admissions, results, years, levels);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        session = new MockHttpSession();
        session.setAttribute("authenticatedUserId", 1L);
        session.setAttribute("authenticatedUserRole", "ROLE_MEMBER");
        Member member = new Member();
        member.setAccountStatus(AccountStatus.ACTIVE);
        when(members.findById(1L)).thenReturn(Optional.of(member));
        when(results.memberApplications(1L, 0, 25)).thenReturn(
                new SliceImpl<>(List.of(new MemberApplication("APP-1", "Secondary 1")), PageRequest.of(0, 25), true));
        when(results.memberResult(1L, "APP-1")).thenReturn(new MemberApplicationResult(
                "APP-1", "Secondary 1", ApplicationStatus.Success, LocalDateTime.now(), LocalDateTime.now().plusDays(7)));
        when(years.getActiveAcademicYear()).thenReturn(
                new AcademicYearResponse(1L, "Year", LocalDate.now(), null, AcademicYearStatus.ACTIVE));
        when(levels.list(1L)).thenReturn(List.of(new ProgrammeLevelResponse(
                1L, 1L, LevelCode.S1, "Secondary 1", 10, LocalDate.now().minusDays(1), LocalDate.now().plusDays(1))));
    }

    @Test
    void loginPageRendersCsrfAndRealTokenLogsInWithoutEchoingPassword() throws Exception {
        when(login.login("member@example.com", "password", LoginAccountType.MEMBER)).thenReturn(
                new LoginResult(new LoginResponse(1L, "member@example.com", "Test", "Member", ActorRole.ROLE_MEMBER, false), null));
        var page = mvc.perform(get("/login")).andExpect(status().isOk()).andExpect(view().name("auth/login")).andReturn();
        MockHttpSession anonymous = (MockHttpSession) page.getRequest().getSession(false);
        String oldId = anonymous.getId();
        anonymous.setAttribute("offerConfirmation", "previous user");
        mvc.perform(post("/login").session(anonymous).param("_csrf", hidden(page.getResponse().getContentAsString(), "_csrf"))
                        .param("email", "member@example.com").param("password", "password").param("accountType", "MEMBER"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/member/applications"));
        assertNotEquals(oldId, anonymous.getId());
        assertNull(anonymous.getAttribute("offerConfirmation"));
        mvc.perform(get("/member/applications").session(anonymous)).andExpect(status().isOk());
        verify(rateLimits).checkLoginAllowed(anyString(), eq("Too many login attempts"));

        when(login.login(anyString(), anyString(), any())).thenThrow(
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        mvc.perform(post("/login").with(csrf()).param("email", "member@example.com")
                        .param("password", "DO-NOT-ECHO-ME").param("accountType", "MEMBER"))
                .andExpect(status().isUnauthorized()).andExpect(view().name("auth/login"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("DO-NOT-ECHO-ME"))));
        mvc.perform(post("/login").with(csrf()).param("email", "invalid"))
                .andExpect(status().isBadRequest()).andExpect(view().name("auth/login"));
    }

    @Test
    void protectedPagesAndApisEnforceAuthenticationRoleAndCsrf() throws Exception {
        mvc.perform(get("/member/applications")).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/api/member/applications")).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/api/public/unlisted")).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/api/admin/applications").session(session)).andExpect(status().isForbidden());
        session.setAttribute("authenticatedUserRole", "ROLE_ADMIN");
        mvc.perform(get("/member/applications").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/super-admin/admissions-officers").session(session)).andExpect(status().isForbidden());
        when(accounts.requireAdmin(any())).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));
        mvc.perform(get("/api/admin/applications").session(session)).andExpect(redirectedUrl("/login"));
        session.setAttribute("authenticatedUserRole", "ROLE_MEMBER");
        Member suspended = new Member();
        suspended.setAccountStatus(AccountStatus.SUSPENDED);
        when(members.findById(1L)).thenReturn(Optional.of(suspended));
        mvc.perform(get("/member/applications").session(session)).andExpect(redirectedUrl("/login"));
        mvc.perform(post("/login")).andExpect(status().isForbidden());
        mvc.perform(post("/logout").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/api/public/auth/login")).andExpect(status().isForbidden());
        mvc.perform(post("/api/public/membership/register")).andExpect(status().isForbidden());
        verifyNoInteractions(admissions);
    }

    @Test
    void applicationPagesRenderAndBindIsoDateWithPostRedirectGet() throws Exception {
        mvc.perform(get("/member/applications").session(session)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("APP-1")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("page=1")));
        mvc.perform(get("/member/applications/new").session(session)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Secondary 1")));
        mvc.perform(post("/member/applications").session(session).with(csrf())
                        .param("levelCode", "S1").param("applicantFullName", "Applicant")
                        .param("applicantIdentityDocumentNumber", "P123456")
                        .param("applicantIdentityDocumentType", "PASSPORT")
                        .param("applicantIdentityDocumentIssuingCountry", "HK").param("dateOfBirth", "2018-01-01")
                        .param("gender", "Unspecified").param("phone", "12345678").param("previousSchool", "School"))
                .andExpect(redirectedUrl("/member/applications"));
        verify(admissions).submitApplication(argThat(form -> form.dateOfBirth().equals(LocalDate.of(2018, 1, 1))), eq(1L));
        mvc.perform(post("/member/applications").session(session).with(csrf()).param("dateOfBirth", "not-a-date"))
                .andExpect(status().isBadRequest()).andExpect(view().name("member/application-form"))
                .andExpect(model().attributeHasFieldErrors("applicationForm", "dateOfBirth"));
    }

    @Test
    void closedApplicationsAndDocumentErrorsStayOnForm() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Application period is closed."))
                .when(admissions).submitApplication(any(), eq(1L));
        mvc.perform(validApplication()).andExpect(status().isConflict()).andExpect(view().name("member/application-form"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Application period is closed.")));
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid document number"))
                .when(admissions).submitApplication(any(), eq(1L));
        mvc.perform(validApplication()).andExpect(status().isBadRequest()).andExpect(view().name("member/application-form"));
        when(years.getActiveAcademicYear()).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "No open year"));
        mvc.perform(get("/member/applications/new").session(session)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("No open year")));
    }

    @Test
    void offerRequiresConfirmationRejectsTamperingAndCannotBeReplayed() throws Exception {
        when(results.offer(1L, "APP-1", OfferDecisionRequest.Decision.Accept)).thenReturn(
                new OfferConfirmation("Secondary 1", "APP-1", LocalDateTime.now().plusDays(7),
                        LocalDateTime.now(), OfferDecisionRequest.Decision.Accept, null));
        mvc.perform(get("/member/applications/APP-1").session(session)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Accept offer")));
        var page = mvc.perform(get("/member/applications/APP-1/offer").session(session).param("decision", "Accept"))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("cannot be changed")))
                .andReturn();
        String token = hidden(page.getResponse().getContentAsString(), "confirmationToken");
        mvc.perform(post("/member/applications/APP-1/offer-response").session(session).with(csrf())
                        .param("decision", "Reject").param("confirmationToken", token))
                .andExpect(redirectedUrl("/member/applications/APP-1")).andExpect(flash().attributeExists("error"));
        verify(results, never()).respondToOffer(anyLong(), anyString(), any(), any());
        when(results.respondToOffer(eq(1L), eq("APP-1"), eq(OfferDecisionRequest.Decision.Accept), any())).thenReturn(true);
        mvc.perform(post("/member/applications/APP-1/offer-response").session(session)
                        .param("_csrf", hidden(page.getResponse().getContentAsString(), "_csrf"))
                        .param("decision", "Accept").param("confirmationToken", token))
                .andExpect(redirectedUrl("/member/applications/APP-1")).andExpect(flash().attributeExists("message"));
        mvc.perform(post("/member/applications/APP-1/offer-response").session(session).with(csrf())
                        .param("decision", "Accept").param("confirmationToken", token))
                .andExpect(flash().attributeExists("error"));
        verify(results, times(1)).respondToOffer(eq(1L), eq("APP-1"), any(), any());
    }

    @Test
    void expiredOffersAndOtherMembersApplicationsCannotBeActedOn() throws Exception {
        when(results.offer(1L, "APP-1", OfferDecisionRequest.Decision.Accept)).thenReturn(null);
        mvc.perform(get("/member/applications/APP-1/offer").session(session).param("decision", "Accept"))
                .andExpect(redirectedUrl("/member/applications/APP-1")).andExpect(flash().attributeExists("error"));
        when(results.memberResult(1L, "OTHER")).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        mvc.perform(get("/member/applications/OTHER").session(session)).andExpect(status().isNotFound());
        verify(results).memberResult(1L, "OTHER");
        mvc.perform(post("/member/applications/APP-1/offer-response").session(session).with(csrf()))
                .andExpect(flash().attributeExists("error"));
        verify(results, never()).respondToOffer(anyLong(), anyString(), any(), any());
    }

    @Test
    void expiryBetweenConfirmationAndSubmitShowsErrorAndClearsConfirmation() throws Exception {
        when(results.offer(1L, "APP-1", OfferDecisionRequest.Decision.Accept)).thenReturn(
                new OfferConfirmation("Secondary 1", "APP-1", LocalDateTime.now().plusMinutes(1),
                        LocalDateTime.now(), OfferDecisionRequest.Decision.Accept, null));
        mvc.perform(get("/member/applications/APP-1/offer").session(session).param("decision", "Accept"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
        OfferConfirmation offer = (OfferConfirmation) session.getAttribute("offerConfirmation");
        when(results.respondToOffer(1L, "APP-1", OfferDecisionRequest.Decision.Accept, offer)).thenReturn(false);
        mvc.perform(post("/member/applications/APP-1/offer-response").session(session).with(csrf())
                        .param("decision", "Accept").param("confirmationToken", offer.confirmationToken()))
                .andExpect(redirectedUrl("/member/applications/APP-1")).andExpect(flash().attributeExists("error"));
        assertNull(session.getAttribute("offerConfirmation"));
    }

    @Test
    void rejectOfferAndLogoutUsePageRoutes() throws Exception {
        when(results.offer(1L, "APP-1", OfferDecisionRequest.Decision.Reject)).thenReturn(
                new OfferConfirmation("Secondary 1", "APP-1", LocalDateTime.now().plusDays(7),
                        LocalDateTime.now(), OfferDecisionRequest.Decision.Reject, null));
        mvc.perform(get("/member/applications/APP-1/offer").session(session).param("decision", "Reject"))
                .andExpect(status().isOk()).andExpect(view().name("member/offer-confirmation"));
        OfferConfirmation offer = (OfferConfirmation) session.getAttribute("offerConfirmation");
        when(results.respondToOffer(1L, "APP-1", OfferDecisionRequest.Decision.Reject, offer)).thenReturn(true);
        mvc.perform(post("/member/applications/APP-1/offer-response").session(session).with(csrf())
                        .param("decision", "Reject").param("confirmationToken", offer.confirmationToken()))
                .andExpect(flash().attributeExists("message"));
        mvc.perform(post("/logout").session(session).with(csrf())).andExpect(redirectedUrl("/login?logout"));
        assertTrue(session.isInvalid());
    }

    @Test
    void replacedJsonRoutesAndTokenEndpointAreDenied() throws Exception {
        mvc.perform(get("/api/member/applications").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/member/applications/APP-1/result").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/member/applications/APP-1/offer").session(session).param("decision", "Accept"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/member/applications").session(session).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(put("/api/member/applications/APP-1/offer-response").session(session).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/public/auth/login").session(session).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/api/public/auth/logout").session(session).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(get("/api/public/auth/csrf").session(session)).andExpect(status().isForbidden());
        verifyNoInteractions(admissions);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder validApplication() {
        return post("/member/applications").session(session).with(csrf())
                .param("levelCode", "S1").param("applicantFullName", "Applicant")
                .param("applicantIdentityDocumentNumber", "P123456").param("applicantIdentityDocumentType", "PASSPORT")
                .param("applicantIdentityDocumentIssuingCountry", "HK").param("dateOfBirth", "2018-01-01")
                .param("gender", "Unspecified").param("phone", "12345678").param("previousSchool", "School");
    }

    private String hidden(String html, String name) {
        var field = Pattern.compile("<input[^>]*name=\"" + name + "\"[^>]*value=\"([^\"]*)\"").matcher(html);
        assertTrue(field.find(), "Missing hidden field " + name);
        return field.group(1);
    }
}
