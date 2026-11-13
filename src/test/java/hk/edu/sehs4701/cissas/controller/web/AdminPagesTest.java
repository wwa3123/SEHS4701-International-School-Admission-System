package hk.edu.sehs4701.cissas.controller.web;

import hk.edu.sehs4701.cissas.dto.internal.PasswordSession;
import hk.edu.sehs4701.cissas.dto.response.*;
import hk.edu.sehs4701.cissas.enums.*;
import hk.edu.sehs4701.cissas.entity.Member;
import hk.edu.sehs4701.cissas.repository.MemberRepository;
import hk.edu.sehs4701.cissas.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;

@SpringJUnitConfig(AdminPagesTest.TestConfiguration.class)
@WebAppConfiguration
class AdminPagesTest {
    @Configuration(proxyBeanMethods = false)
    @Import({MemberPagesTest.TestConfiguration.class, AdminApplicationPageController.class,
            AdminEmailPageController.class, AdminPasswordPageController.class, AdminPageErrorHandler.class})
    static class TestConfiguration {
        @Bean EmailService emails() { return mock(EmailService.class); }
        @Bean IdentityReviewService duplicates() { return mock(IdentityReviewService.class); }
    }

    @Autowired private WebApplicationContext context;
    @Autowired private AdminAccountService accounts;
    @Autowired private ApplicationResultService results;
    @Autowired private EmailService emails;
    @Autowired private IdentityReviewService duplicates;
    @Autowired private RateLimitService limits;
    @Autowired private MemberRepository members;
    @Autowired private LoginService login;
    private MockMvc mvc;
    private MockHttpSession session;

    @BeforeEach
    void setup() {
        reset(accounts, results, emails, duplicates, limits, members, login);
        Member member = new Member();
        member.setAccountStatus(AccountStatus.ACTIVE);
        when(members.findById(42L)).thenReturn(java.util.Optional.of(member));
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        session = new MockHttpSession();
        session.setAttribute("authenticatedUserId", 42L);
        session.setAttribute("authenticatedUserRole", "ROLE_ADMIN");
        session.setAttribute("adminCredentialVersion", 3L);
        when(accounts.requireAdmin(any())).thenReturn(42L);
        when(accounts.requireAdminForUpdate(any())).thenReturn(42L);
        when(accounts.requirePasswordSession(any())).thenReturn(new PasswordSession(42L, 3L));
        when(results.listApplications(42L, null, null, 0, 25)).thenReturn(new SliceImpl<>(
                List.of(new AdminApplication("APP-1", 1L, "Secondary 1", "Applicant", ApplicationStatus.Pending)),
                PageRequest.of(0, 25), true));
        when(results.waitingList(42L, 1L)).thenReturn(List.of(
                new ApplicationSummary("APP-1", "Applicant", LocalDate.of(2010, 1, 1), IdentityDocumentType.PASSPORT, "Wait")));
        var summary = new ApplicationSummary("APP-1", "Applicant", LocalDate.of(2010, 1, 1), IdentityDocumentType.PASSPORT, "Pending");
        when(duplicates.pending(42L, null, 25)).thenReturn(new CandidatePage(List.of(new Candidate(summary, summary)), "1:2"));
        when(duplicates.history(42L, null, 25)).thenReturn(new HistoryPage(List.of(
                new Decision("APP-1", "APP-2", false, "42", LocalDateTime.now(), true)), "5"));
        when(duplicates.evidence(eq(42L), anyString())).thenReturn(new ApplicationEvidence(
                "APP-1", "member@example.test", "Applicant", LocalDate.of(2010, 1, 1), "Unspecified",
                "12345678", "School", IdentityDocumentType.PASSPORT, "P123456", "HK", "Year", "Pending"));
        when(emails.failedApplicationEmails(0)).thenReturn(new SliceImpl<>(List.of(
                new FailedApplicationEmail(1L, "APP-1", EmailTemplateType.RESULT_ANNOUNCEMENT, "SMTP unavailable")),
                PageRequest.of(0, 100), true));
    }

    @Test
    void allAdministratorPagesRenderDtosAndCsrfForms() throws Exception {
        for (String path : List.of("/admin/applications", "/admin/applications/APP-1/result",
                "/admin/applications/APP-1/acceptance-deadline", "/admin/applications/possible-duplicates",
                "/admin/applications/possible-duplicates/history", "/admin/applications/possible-duplicates/applications/APP-1",
                "/admin/emails/failed", "/admin/emails/1/skip", "/admin/password")) {
            mvc.perform(get(path).session(session)).andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", containsString("no-store")));
        }

        mvc.perform(get("/admin/applications/waiting-list").session(session).param("programmeLevelId", "1"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("not ranked")));
        mvc.perform(get("/admin/applications/possible-duplicates/review").session(session)
                        .param("duplicateApplicationNumber", "APP-1").param("matchingApplicationNumber", "APP-2"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("_csrf")));
    }

    @Test
    void administratorLoginRedirectsToPasswordChangeOrApplicationManagement() throws Exception {
        for (boolean changeRequired : List.of(true, false)) {
            when(login.login("admin@example.test", "password", LoginAccountType.ADMIN)).thenReturn(
                    new hk.edu.sehs4701.cissas.dto.internal.LoginResult(new LoginResponse(42L, "admin@example.test",
                            "Admin", "User", ActorRole.ROLE_ADMIN, changeRequired), 3L));
            mvc.perform(post("/login").with(csrf()).param("email", "admin@example.test")
                            .param("password", "password").param("accountType", "ADMIN"))
                    .andExpect(redirectedUrl(changeRequired ? "/admin/password" : "/admin/applications"));
        }
    }

    @Test
    void resultsValidateReleaseAndShowQuotaConflicts() throws Exception {
        mvc.perform(post("/admin/applications/APP-1/result").session(session).with(csrf()))
                .andExpect(status().isBadRequest()).andExpect(view().name("admin/result-form"));
        verify(results, never()).setResult(anyLong(), anyString(), any(), any());
        when(results.setResult(42L, "APP-1", ApplicationStatus.Success, "")).thenReturn(false);
        mvc.perform(post("/admin/applications/APP-1/result").session(session).with(csrf())
                        .param("result", "Success").param("reason", ""))
                .andExpect(status().isConflict()).andExpect(content().string(containsString("seat quota")));
        when(results.setResult(42L, "APP-1", ApplicationStatus.Wait, "")).thenReturn(true);
        for (String role : List.of("ROLE_ADMIN", "ROLE_SUPER_ADMIN")) {
            session.setAttribute("authenticatedUserRole", role);
            mvc.perform(post("/admin/applications/APP-1/result").session(session).with(csrf())
                            .param("result", "Wait").param("reason", ""))
                    .andExpect(redirectedUrl("/admin/applications")).andExpect(flash().attributeExists("message"));
        }
        when(results.setResult(42L, "APP-1", ApplicationStatus.Fail, "Correction"))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Only a super administrator may correct a result"));
        mvc.perform(post("/admin/applications/APP-1/result").session(session).with(csrf())
                        .param("result", "Fail").param("reason", "Correction"))
                .andExpect(status().isForbidden()).andExpect(view().name("admin/action-error"));
    }

    @Test
    void deadlineBindingAndExpiredOfferAreHandledWithoutSuccessMessages() throws Exception {
        LocalDateTime deadline = LocalDateTime.of(2027, 1, 1, 12, 0);
        mvc.perform(post("/admin/applications/APP-1/acceptance-deadline").session(session).with(csrf())
                        .param("acceptanceDeadline", "invalid").param("reason", "System fault"))
                .andExpect(status().isBadRequest()).andExpect(view().name("admin/deadline-form"));
        mvc.perform(post("/admin/applications/APP-1/acceptance-deadline").session(session).with(csrf())
                        .param("acceptanceDeadline", "2027-01-01T12:00").param("reason", "System fault"))
                .andExpect(status().isConflict()).andExpect(content().string(containsString("expired")));
        when(results.extendDeadline(42L, "APP-1", deadline, "System fault")).thenReturn(true);
        mvc.perform(post("/admin/applications/APP-1/acceptance-deadline").session(session).with(csrf())
                        .param("acceptanceDeadline", "2027-01-01T12:00").param("reason", "System fault"))
                .andExpect(redirectedUrl("/admin/applications"));
    }

    @Test
    void duplicateReviewAndEmailActionsUseAdministratorIdAndValidateReasons() throws Exception {
        mvc.perform(post("/admin/applications/possible-duplicates").session(session).with(csrf())
                        .param("duplicateApplicationNumber", "APP-1").param("matchingApplicationNumber", "APP-2"))
                .andExpect(status().isBadRequest()).andExpect(view().name("admin/duplicate-review"));
        mvc.perform(post("/admin/applications/possible-duplicates").session(session).with(csrf())
                        .param("duplicateApplicationNumber", "APP-1").param("matchingApplicationNumber", "APP-2").param("samePerson", "true"))
                .andExpect(redirectedUrl("/admin/applications/possible-duplicates"));
        verify(duplicates).review(42L, "APP-1", "APP-2", true);
        mvc.perform(post("/admin/emails/1/resend").session(session).with(csrf()))
                .andExpect(redirectedUrl("/admin/emails/failed"));
        mvc.perform(post("/admin/emails/1/skip").session(session).with(csrf()).param("reason", ""))
                .andExpect(status().isBadRequest()).andExpect(view().name("admin/skip-email"));
        verify(emails, never()).skipFailedApplicationEmail(anyLong(), anyLong(), anyString());
        mvc.perform(post("/admin/emails/1/skip").session(session).with(csrf()).param("reason", "Undeliverable"))
                .andExpect(redirectedUrl("/admin/emails/failed"));
        verify(emails).skipFailedApplicationEmail(1L, 42L, "Undeliverable");
        doThrow(new IllegalStateException("Already sent")).when(emails).manualResend(1L);
        mvc.perform(post("/admin/emails/1/resend").session(session).with(csrf()))
                .andExpect(status().isConflict()).andExpect(content().string(containsString("Already sent")));
    }

    @Test
    void forcedPasswordChangeRemainsAccessibleAndOnlySuccessSignsOut() throws Exception {
        when(accounts.requireAdmin(any())).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Password change required"));
        mvc.perform(get("/admin/password").session(session)).andExpect(status().isOk());
        mvc.perform(get("/admin/applications").session(session)).andExpect(redirectedUrl("/login"));
        doThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Incorrect current password"))
                .when(accounts).changePassword(any(), any());
        mvc.perform(post("/admin/password").session(session).with(csrf())
                        .param("currentPassword", "DO-NOT-ECHO").param("newPassword", "new-password"))
                .andExpect(status().isUnauthorized()).andExpect(view().name("admin/password"))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("DO-NOT-ECHO"))));
        assertFalse(session.isInvalid());
        doNothing().when(accounts).changePassword(any(), any());
        mvc.perform(post("/admin/password").session(session).with(csrf())
                        .param("currentPassword", "old-password").param("newPassword", "new-password"))
                .andExpect(redirectedUrl("/login?passwordChanged"));
        assertTrue(session.isInvalid());
        verify(limits, times(2)).checkAllowed("admin-password-account:42:3", "Too many password attempts");
    }

    @Test
    void passwordLimitStopsVerificationAndIsBoundToValidatedAccount() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many password attempts"))
                .when(limits).checkAllowed("admin-password-account:42:3", "Too many password attempts");
        mvc.perform(post("/admin/password").session(session).with(csrf())
                        .param("currentPassword", "password").param("newPassword", "new-password"))
                .andExpect(status().isTooManyRequests());
        verify(accounts, never()).changePassword(any(), any());
        assertFalse(session.isInvalid());
        when(accounts.requirePasswordSession(any())).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));
        mvc.perform(post("/admin/password").session(session).with(csrf())).andExpect(redirectedUrl("/login"));
        verify(limits, times(1)).checkAllowed(anyString(), anyString());
    }

    @Test
    void anonymousWrongRolesAndMissingCsrfCannotChangeResults() throws Exception {
        mvc.perform(get("/admin/applications")).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/super-admin/admissions-officers")).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/super-admin/academic-years").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/super-admin/admissions-officers").session(session).with(csrf()))
                .andExpect(status().isForbidden());
        session.setAttribute("authenticatedUserRole", "ROLE_MEMBER");
        mvc.perform(post("/admin/applications/APP-1/result").session(session).with(csrf()).param("result", "Success"))
                .andExpect(status().isForbidden());
        session.setAttribute("authenticatedUserRole", "ROLE_ADMIN");
        mvc.perform(post("/admin/applications/APP-1/result").session(session).param("result", "Success"))
                .andExpect(status().isForbidden());
        verify(results, never()).setResult(anyLong(), anyString(), any(), any());
    }
}
