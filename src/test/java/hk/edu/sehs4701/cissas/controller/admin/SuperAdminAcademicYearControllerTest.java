package hk.edu.sehs4701.cissas.controller.admin;

import hk.edu.sehs4701.cissas.controller.api.admin.SuperAdminAcademicYearController;
import hk.edu.sehs4701.cissas.dto.response.AcademicYearResponse;
import hk.edu.sehs4701.cissas.enums.AcademicYearStatus;
import hk.edu.sehs4701.cissas.enums.ActorRole;
import hk.edu.sehs4701.cissas.enums.LevelCode;
import hk.edu.sehs4701.cissas.entity.Admin;
import hk.edu.sehs4701.cissas.repository.AdminRepository;
import hk.edu.sehs4701.cissas.service.AdminAccountService;
import hk.edu.sehs4701.cissas.service.AcademicYearService;
import hk.edu.sehs4701.cissas.service.ProgrammeLevelService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SuperAdminAcademicYearControllerTest {
    private final AcademicYearService service = mock(AcademicYearService.class);
    private final AdminRepository admins = mock(AdminRepository.class);
    private final AdminAccountService accounts = new AdminAccountService(admins, null, null, null);
    private final ProgrammeLevelService programmes = mock(ProgrammeLevelService.class);
    private final SuperAdminAcademicYearController controller =
            new SuperAdminAcademicYearController(service, accounts, programmes);

    @Test
    void deniesRegularAdmins() {
        MockHttpServletRequest request = requestWithRole("ROLE_ADMIN");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> controller.create(new SuperAdminAcademicYearController.CreateRequest(
                        "2028/29", LocalDate.of(2028, 9, 1), LocalDate.of(2029, 8, 31)), request));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verifyNoInteractions(service);
    }

    @Test
    void letsSuperAdminsCreateAcademicYears() {
        MockHttpServletRequest request = requestWithRole("ROLE_SUPER_ADMIN");
        Admin admin = new Admin();
        admin.setRole(ActorRole.ROLE_SUPER_ADMIN);
        when(admins.findById(42L)).thenReturn(Optional.of(admin));
        when(admins.findLockedById(42L)).thenReturn(Optional.of(admin));
        var createRequest = new SuperAdminAcademicYearController.CreateRequest(
                "2028/29", LocalDate.of(2028, 9, 1), LocalDate.of(2029, 8, 31));
        AcademicYearResponse response = new AcademicYearResponse(
                1L, "2028/29", createRequest.applicationStartDate(), createRequest.applicationEndDate(),
                AcademicYearStatus.PLANNING);
        when(service.create("2028/29", createRequest.applicationStartDate(), createRequest.applicationEndDate()))
                .thenReturn(response);

        assertEquals(response, controller.create(createRequest, request));
    }

    @Test
    void statusUpdateRequiresAnActiveSuperAdmin() {
        MockHttpServletRequest request = requestWithRole("ROLE_SUPER_ADMIN");
        Admin admin = new Admin();
        admin.setRole(ActorRole.ROLE_SUPER_ADMIN);
        admin.setActive(false);
        when(admins.findById(42L)).thenReturn(Optional.of(admin));
        when(admins.findLockedById(42L)).thenReturn(Optional.of(admin));
        var update = new SuperAdminAcademicYearController.LifecycleRequest(AcademicYearStatus.ACTIVE);

        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> controller.updateStatus(1L, update, requestWithRole("ROLE_ADMIN"))).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class,
                () -> controller.updateStatus(1L, update, new MockHttpServletRequest())).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> controller.updateStatus(1L, update, request)).getStatusCode());
        admin.setActive(true);
        controller.updateStatus(1L, update, request);
        verify(service).updateLifecycle(1L, AcademicYearStatus.ACTIVE);
        admin.setRole(ActorRole.ROLE_ADMIN);
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> controller.updateStatus(1L, update, request)).getStatusCode());
    }

    @Test
    void onlySuperAdminsCanCreateProgrammeLevels() {
        var programme = new SuperAdminAcademicYearController.ProgrammeRequest(
                LevelCode.S1, "Secondary", 150, LocalDateTime.of(2028, 9, 15, 17, 0));
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> controller.createProgramme(1L, programme, requestWithRole("ROLE_ADMIN"))).getStatusCode());
        verifyNoInteractions(programmes);

        Admin admin = new Admin();
        admin.setRole(ActorRole.ROLE_SUPER_ADMIN);
        when(admins.findById(42L)).thenReturn(Optional.of(admin));
        when(admins.findLockedById(42L)).thenReturn(Optional.of(admin));
        controller.createProgramme(1L, programme, requestWithRole("ROLE_SUPER_ADMIN"));
        verify(programmes).create(1L, LevelCode.S1, "Secondary", 150, programme.applicationDeadline());
    }

    @Test
    void onlySuperAdminsCanUpdateProgrammeLevels() {
        var update = new SuperAdminAcademicYearController.ProgrammeUpdateRequest(
                "Corrected", 100, LocalDateTime.of(2028, 9, 15, 17, 0));
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> controller.updateProgramme(1L, 10L, update, requestWithRole("ROLE_ADMIN"))).getStatusCode());
        verifyNoInteractions(programmes);
        Admin admin = new Admin();
        admin.setRole(ActorRole.ROLE_SUPER_ADMIN);
        when(admins.findById(42L)).thenReturn(Optional.of(admin));
        when(admins.findLockedById(42L)).thenReturn(Optional.of(admin));
        controller.updateProgramme(1L, 10L, update, requestWithRole("ROLE_SUPER_ADMIN"));
        verify(programmes).update(1L, 10L, "Corrected", 100, update.applicationDeadline());
    }

    @Test
    void programmeRequestRejectsNegativeQuotaOverHttp() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("authenticatedUserId", 42L);
        session.setAttribute("adminCredentialVersion", 0L);
        session.setAttribute("authenticatedUserRole", "ROLE_SUPER_ADMIN");
        MockMvcBuilders.standaloneSetup(controller).build()
                .perform(post("/api/super-admin/academic-years/1/programme-levels")
                        .session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"levelCode":"S1","programmeName":"Secondary",
                                 "seatQuota":-1,"applicationDeadline":"2028-09-15T17:00:00"}
                                """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(programmes);
    }

    @Test
    void programmeUpdateRejectsNegativeQuotaOverHttp() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("authenticatedUserId", 42L);
        session.setAttribute("adminCredentialVersion", 0L);
        session.setAttribute("authenticatedUserRole", "ROLE_SUPER_ADMIN");
        MockMvcBuilders.standaloneSetup(controller).build()
                .perform(put("/api/super-admin/academic-years/1/programme-levels/10")
                        .session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"programmeName":"Secondary",
                                 "seatQuota":-1,"applicationDeadline":"2028-09-15T17:00:00"}
                                """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(programmes);
    }

    @Test
    void listProgrammesRequiresSuperAdminAndDisablesCaching() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("authenticatedUserId", 42L);
        session.setAttribute("authenticatedUserRole", "ROLE_ADMIN");
        session.setAttribute("adminCredentialVersion", 0L);
        mvc.perform(get("/api/super-admin/academic-years/1/programme-levels").session(session))
                .andExpect(status().isForbidden());
        Admin admin = new Admin();
        admin.setRole(ActorRole.ROLE_SUPER_ADMIN);
        when(admins.findLockedById(42L)).thenReturn(Optional.of(admin));
        session.setAttribute("authenticatedUserRole", "ROLE_SUPER_ADMIN");
        mvc.perform(get("/api/super-admin/academic-years/1/programme-levels").session(session))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Cache-Control", "no-store"));
        verify(programmes).list(1L);
        verify(admins, times(2)).findLockedById(42L);
    }

    private MockHttpServletRequest requestWithRole(String role) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("authenticatedUserRole", role);
        session.setAttribute("authenticatedUserId", 42L);
        session.setAttribute("adminCredentialVersion", 0L);
        request.setSession(session);
        return request;
    }
}
