package hk.edu.sehs4701.cissas.controller.web;

import hk.edu.sehs4701.cissas.dto.request.*;
import hk.edu.sehs4701.cissas.dto.response.*;
import hk.edu.sehs4701.cissas.enums.*;
import hk.edu.sehs4701.cissas.exception.SeatQuotaExceededException;
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
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;

import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(AcademicAdministrationPagesTest.TestConfiguration.class)
@WebAppConfiguration
class AcademicAdministrationPagesTest {
    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfiguration.class, AdmissionsOfficerPageController.class,
            SuperAdminAcademicYearPageController.class, AcademicYearPageController.class})
    static class TestConfiguration {
        @Bean AdminAccountService accounts() { return mock(AdminAccountService.class); }
        @Bean AcademicYearService years() { return mock(AcademicYearService.class); }
        @Bean ProgrammeLevelService levels() { return mock(ProgrammeLevelService.class); }
        @Bean MemberRepository members() { return mock(MemberRepository.class); }

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
    @Autowired private AdminAccountService accounts;
    @Autowired private AcademicYearService years;
    @Autowired private ProgrammeLevelService levels;
    private MockMvc mvc;
    private MockHttpSession session;
    private AcademicYearResponse year;
    private ProgrammeLevelResponse level;

    @BeforeEach
    void setup() {
        reset(accounts, years, levels);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        session = new MockHttpSession();
        session.setAttribute("authenticatedUserId", 1L);
        session.setAttribute("authenticatedUserRole", "ROLE_SUPER_ADMIN");
        session.setAttribute("adminCredentialVersion", 0L);
        year = new AcademicYearResponse(1L, "2028/29", LocalDate.of(2028, 1, 1), null, AcademicYearStatus.PLANNING);
        level = new ProgrammeLevelResponse(10L, 1L, LevelCode.S1, "Secondary 1", 20,
                LocalDate.of(2028, 9, 1), LocalDate.of(2028, 9, 30));
        when(years.list()).thenReturn(List.of(year));
        when(years.get(1L)).thenReturn(year);
        when(years.getCurrentOpenForApplicationAcademicYear()).thenReturn(year);
        when(levels.list(1L)).thenReturn(List.of(level));
        when(accounts.listOfficers()).thenReturn(List.of(
                new AccountResponse("42", "officer@example.com", "First", "Last", true)));
    }

    @Test
    void anonymousDiscoveryPagesRenderProgrammeDetailsAndUsefulLinks() throws Exception {
        mvc.perform(get("/academic-years")).andExpect(status().isOk())
                .andExpect(view().name("public/academic-years"))
                .andExpect(content().string(containsString("/academic-years/1")));
        for (String route : List.of("/academic-years/1", "/academic-years/1/programme-levels",
                "/academic-years/open-for-application")) {
            mvc.perform(get(route)).andExpect(status().isOk()).andExpect(view().name("public/academic-year"))
                    .andExpect(content().string(containsString("Secondary 1")))
                    .andExpect(content().string(containsString("2028-09-01")))
                    .andExpect(content().string(containsString("/member/applications/new")));
        }
        verifyNoInteractions(accounts);
    }

    @Test
    void discoveryWithoutAnOpenYearShowsAnExplanationAndYearList() throws Exception {
        when(years.getCurrentOpenForApplicationAcademicYear()).thenThrow(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "No academic year open for applications found"));
        mvc.perform(get("/academic-years/open-for-application")).andExpect(status().isOk())
                .andExpect(view().name("public/academic-years"))
                .andExpect(content().string(containsString("No academic year open for applications found")));
        when(years.get(99L)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Academic year not found"));
        mvc.perform(get("/academic-years/99")).andExpect(status().isNotFound());
    }

    @Test
    void managementPagesRequireSuperAdminAndPostRequiresCsrf() throws Exception {
        mvc.perform(get("/super-admin/admissions-officers")).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/super-admin/academic-years")).andExpect(redirectedUrl("/login"));
        session.setAttribute("authenticatedUserRole", "ROLE_ADMIN");
        mvc.perform(get("/super-admin/admissions-officers").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/super-admin/academic-years").session(session).with(csrf()).param("name", "Year"))
                .andExpect(status().isForbidden());
        session.setAttribute("authenticatedUserRole", "ROLE_SUPER_ADMIN");
        mvc.perform(post("/super-admin/admissions-officers").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/super-admin/academic-years").session(session)).andExpect(status().isForbidden());
        verify(accounts, never()).requireSuperAdminForUpdate(any());
        verify(years, never()).create(anyString());
    }

    @Test
    void databaseGuardRunsBeforeEveryActionIncludingInvalidForms() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Super admin access required"))
                .when(accounts).requireSuperAdminForUpdate(any());
        for (String route : List.of("/super-admin/admissions-officers", "/super-admin/admissions-officers/new",
                "/super-admin/admissions-officers/42/password", "/super-admin/academic-years",
                "/super-admin/academic-years/new", "/super-admin/academic-years/1",
                "/super-admin/academic-years/1/programme-levels/new",
                "/super-admin/academic-years/1/programme-levels/10/edit")) {
            mvc.perform(get(route).session(session)).andExpect(status().isForbidden());
        }
        for (String route : List.of("/super-admin/admissions-officers",
                "/super-admin/admissions-officers/42/password", "/super-admin/admissions-officers/42/deactivate",
                "/super-admin/academic-years", "/super-admin/academic-years/1/status",
                "/super-admin/academic-years/1/programme-levels", "/super-admin/academic-years/1/programme-levels/10")) {
            mvc.perform(post(route).session(session).with(csrf())).andExpect(status().isForbidden());
        }
        verifyNoInteractions(years, levels);
        verify(accounts, never()).createOfficer(any());
        verify(accounts, never()).resetOfficerPassword(anyLong(), any());
        verify(accounts, never()).deactivateOfficer(anyLong());
    }

    @Test
    void officerPagesRenderRealCsrfAndCreateUsesPostRedirectGet() throws Exception {
        mvc.perform(get("/super-admin/admissions-officers").session(session)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(containsString("officer@example.com")));
        var page = mvc.perform(get("/super-admin/admissions-officers/new").session(session))
                .andExpect(status().isOk()).andReturn();
        mvc.perform(officerPost().param("_csrf", csrfValue(page.getResponse().getContentAsString())))
                .andExpect(redirectedUrl("/super-admin/admissions-officers"));
        verify(accounts).createOfficer(new AccountRequest("officer@example.com", "First", "Last", "secret-password"));
    }

    @Test
    void accountValidationAndConflictsRerenderWithoutEchoingPasswords() throws Exception {
        mvc.perform(officerPost().with(csrf()).with(request -> {
                    request.setParameter("email", "invalid");
                    return request;
                }))
                .andExpect(status().isBadRequest()).andExpect(view().name("super-admin/officer-form"))
                .andExpect(model().attributeHasFieldErrors("accountForm", "email"))
                .andExpect(content().string(not(containsString("secret-password"))));
        verify(accounts, never()).createOfficer(any());
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Admin email already exists"))
                .when(accounts).createOfficer(any());
        mvc.perform(officerPost().with(csrf())).andExpect(status().isConflict())
                .andExpect(view().name("super-admin/officer-form"))
                .andExpect(content().string(containsString("Admin email already exists")))
                .andExpect(content().string(not(containsString("secret-password"))));
    }

    @Test
    void officerPasswordResetAndDeactivateUseServicesAndNeverEchoSecrets() throws Exception {
        mvc.perform(get("/super-admin/admissions-officers/42/password").session(session))
                .andExpect(status().isOk()).andExpect(content().string(containsString("_csrf")));
        mvc.perform(post("/super-admin/admissions-officers/42/password").session(session).with(csrf())
                        .param("newPassword", "short")).andExpect(status().isBadRequest())
                .andExpect(model().attributeHasFieldErrors("passwordForm", "newPassword"))
                .andExpect(content().string(not(containsString("value=\"short\""))));
        verify(accounts, never()).resetOfficerPassword(anyLong(), any());
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid password"))
                .when(accounts).resetOfficerPassword(eq(42L), any());
        mvc.perform(post("/super-admin/admissions-officers/42/password").session(session).with(csrf())
                        .param("newPassword", "secret-password")).andExpect(status().isBadRequest())
                .andExpect(view().name("super-admin/officer-password"))
                .andExpect(content().string(not(containsString("secret-password"))));
        doNothing().when(accounts).resetOfficerPassword(eq(42L), any());
        mvc.perform(post("/super-admin/admissions-officers/42/password").session(session).with(csrf())
                        .param("newPassword", "secret-password"))
                .andExpect(redirectedUrl("/super-admin/admissions-officers"));
        verify(accounts, times(2)).resetOfficerPassword(42L, new PasswordResetRequest("secret-password"));
        mvc.perform(post("/super-admin/admissions-officers/42/deactivate").session(session).with(csrf()))
                .andExpect(redirectedUrl("/super-admin/admissions-officers"));
        verify(accounts).deactivateOfficer(42L);
    }

    @Test
    void academicYearGetFormsRenderAndSuccessfulPostsRedirect() throws Exception {
        for (String route : List.of("/super-admin/academic-years", "/super-admin/academic-years/new",
                "/super-admin/academic-years/1", "/super-admin/academic-years/1/programme-levels",
                "/super-admin/academic-years/1/programme-levels/new",
                "/super-admin/academic-years/1/programme-levels/10/edit")) {
            mvc.perform(get(route).session(session)).andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"));
        }
        when(years.create("2028/29")).thenReturn(year);
        mvc.perform(post("/super-admin/academic-years").session(session).with(csrf()).param("name", "2028/29"))
                .andExpect(redirectedUrl("/super-admin/academic-years/1"));
        mvc.perform(post("/super-admin/academic-years/1/status").session(session).with(csrf()).param("status", "ACTIVE"))
                .andExpect(redirectedUrl("/super-admin/academic-years/1"));
        verify(years).updateLifecycle(1L, AcademicYearStatus.ACTIVE);
        mvc.perform(programmePost("/super-admin/academic-years/1/programme-levels").param("levelCode", "S1"))
                .andExpect(redirectedUrl("/super-admin/academic-years/1"));
        verify(levels).create(1L, LevelCode.S1, "Secondary 1", 20, level.applicationStartDate(), level.applicationEndDate());
        mvc.perform(programmePost("/super-admin/academic-years/1/programme-levels/10"))
                .andExpect(redirectedUrl("/super-admin/academic-years/1"));
        verify(levels).update(1L, 10L, "Secondary 1", 20, level.applicationStartDate(), level.applicationEndDate());
    }

    @Test
    void academicYearValidationAndServiceConflictsRerenderForms() throws Exception {
        mvc.perform(post("/super-admin/academic-years").session(session).with(csrf()).param("name", ""))
                .andExpect(status().isBadRequest()).andExpect(model().attributeHasFieldErrors("yearForm", "name"));
        when(years.create("2028/29")).thenThrow(
                new ResponseStatusException(HttpStatus.CONFLICT, "Academic year name already exists"));
        mvc.perform(post("/super-admin/academic-years").session(session).with(csrf()).param("name", "2028/29"))
                .andExpect(status().isConflict()).andExpect(view().name("super-admin/academic-year-form"))
                .andExpect(content().string(containsString("Academic year name already exists")));
        mvc.perform(post("/super-admin/academic-years/1/status").session(session).with(csrf()).param("status", "OTHER"))
                .andExpect(status().isBadRequest()).andExpect(model().attributeHasFieldErrors("lifecycleForm", "status"));
        when(years.updateLifecycle(1L, AcademicYearStatus.ACTIVE)).thenThrow(
                new ResponseStatusException(HttpStatus.CONFLICT, "Another academic year is active"));
        mvc.perform(post("/super-admin/academic-years/1/status").session(session).with(csrf()).param("status", "ACTIVE"))
                .andExpect(status().isConflict()).andExpect(view().name("super-admin/academic-year"))
                .andExpect(content().string(containsString("Another academic year is active")));
    }

    @Test
    void invalidProgrammeFieldsDatesAndQuotasDoNotReachServices() throws Exception {
        mvc.perform(programmePost("/super-admin/academic-years/1/programme-levels").param("levelCode", "S1")
                        .with(request -> {
                            request.setParameter("seatQuota", "-1");
                            return request;
                        }))
                .andExpect(status().isBadRequest()).andExpect(model().attributeHasFieldErrors("programmeForm", "seatQuota"));
        for (String route : List.of("/super-admin/academic-years/1/programme-levels",
                "/super-admin/academic-years/1/programme-levels/10")) {
            for (String field : List.of("applicationStartDate", "applicationEndDate", "seatQuota")) {
                var invalid = mvc.perform(programmePost(route).param("levelCode", "S1")
                                .with(request -> {
                                    request.setParameter(field, "not-a-value");
                                    return request;
                                }))
                        .andExpect(status().isBadRequest()).andExpect(view().name("super-admin/programme-form"))
                        .andExpect(model().attributeHasFieldErrors("programmeForm", field))
                        .andExpect(content().string(containsString("action=\"" + route + "\"")))
                        .andExpect(content().string(containsString("value=\"Secondary 1\"")))
                        .andReturn();
                csrfValue(invalid.getResponse().getContentAsString());
            }
        }
        verify(levels, never()).create(any(), any(), any(), anyInt(), any(), any());
        verify(levels, never()).update(any(), any(), any(), anyInt(), any(), any());
        when(levels.create(any(), any(), any(), anyInt(), any(), any())).thenThrow(
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid programme application period"));
        mvc.perform(programmePost("/super-admin/academic-years/1/programme-levels").param("levelCode", "S1"))
                .andExpect(status().isBadRequest()).andExpect(view().name("super-admin/programme-form"))
                .andExpect(content().string(containsString("Invalid programme application period")));
    }

    @Test
    void refusedQuotaShowsCurrentCountAndKeepsSubmittedValues() throws Exception {
        when(levels.update(any(), any(), any(), anyInt(), any(), any())).thenThrow(new SeatQuotaExceededException(21));
        mvc.perform(programmePost("/super-admin/academic-years/1/programme-levels/10"))
                .andExpect(status().isConflict()).andExpect(view().name("super-admin/programme-form"))
                .andExpect(content().string(containsString("current Success and Accepted count: 21")))
                .andExpect(content().string(containsString("value=\"20\"")));
    }

    @Test
    void controllerTransactionsPreserveGuardIsolationAndQuotaAuditCommit() throws Exception {
        for (Class<?> controller : List.of(AdmissionsOfficerPageController.class, SuperAdminAcademicYearPageController.class)) {
            assertEquals(Isolation.READ_COMMITTED, controller.getAnnotation(Transactional.class).isolation());
        }
        var update = SuperAdminAcademicYearPageController.class.getMethod("updateProgramme", Long.class, Long.class,
                ProgrammeLevelUpdateRequest.class, org.springframework.validation.BindingResult.class,
                jakarta.servlet.http.HttpServletRequest.class, jakarta.servlet.http.HttpServletResponse.class,
                org.springframework.ui.Model.class, org.springframework.web.servlet.mvc.support.RedirectAttributes.class);
        assertEquals(Isolation.READ_COMMITTED, update.getAnnotation(Transactional.class).isolation());
        assertArrayEquals(new Class<?>[]{SeatQuotaExceededException.class},
                update.getAnnotation(Transactional.class).noRollbackFor());
    }

    private MockHttpServletRequestBuilder officerPost() {
        return post("/super-admin/admissions-officers").session(session).param("email", "officer@example.com")
                .param("firstName", "First").param("lastName", "Last").param("password", "secret-password");
    }

    private MockHttpServletRequestBuilder programmePost(String route) {
        return post(route).session(session).with(csrf()).param("programmeName", "Secondary 1")
                .param("seatQuota", "20").param("applicationStartDate", "2028-09-01")
                .param("applicationEndDate", "2028-09-30");
    }

    private String csrfValue(String html) {
        var token = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(html);
        assertTrue(token.find(), "Rendered POST form must contain a CSRF token");
        return token.group(1);
    }
}
