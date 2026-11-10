package hk.edu.sehs4701.cissas.controller.admin;

import hk.edu.sehs4701.cissas.controller.api.admin.ApplicationResultController;
import hk.edu.sehs4701.cissas.entity.Admin;
import hk.edu.sehs4701.cissas.enums.ActorRole;
import hk.edu.sehs4701.cissas.enums.ApplicationStatus;
import hk.edu.sehs4701.cissas.repository.AdminRepository;
import hk.edu.sehs4701.cissas.service.AdminAccountService;
import hk.edu.sehs4701.cissas.service.ApplicationResultService;
import hk.edu.sehs4701.cissas.service.IdentityReviewService;
import hk.edu.sehs4701.cissas.dto.response.CandidatePage;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApplicationResultControllerTest {
    private final ApplicationResultService results = mock(ApplicationResultService.class);
    private final IdentityReviewService duplicates = mock(IdentityReviewService.class);
    private final AdminRepository admins = mock(AdminRepository.class);
    private final AdminAccountService accounts = new AdminAccountService(admins, null, null, null);
    private final ApplicationResultController controller =
            new ApplicationResultController(results, accounts, duplicates);

    @Test
    void bothAdminRolesCanReleaseResults() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        when(results.setResult(42L, "APP-1", ApplicationStatus.Wait, null)).thenReturn(true);
        for (ActorRole role : new ActorRole[]{ActorRole.ROLE_ADMIN, ActorRole.ROLE_SUPER_ADMIN}) {
            Admin admin = new Admin();
            admin.setId(42L);
            admin.setRole(role);
            when(admins.findLockedById(42L)).thenReturn(Optional.of(admin));
            MockHttpSession session = new MockHttpSession();
            session.setAttribute("authenticatedUserRole", role.name());
            session.setAttribute("authenticatedUserId", 42L);
            session.setAttribute("adminCredentialVersion", 0L);

            mvc.perform(put("/api/admin/applications/APP-1/result").session(session)
                            .contentType("application/json").content("{\"result\":\"Wait\"}"))
                    .andExpect(status().isNoContent());
        }
        verify(results, times(2)).setResult(42L, "APP-1", ApplicationStatus.Wait, null);
    }

    @Test
    void possibleDuplicatesAreListedWithinApplicationManagement() throws Exception {
        Admin admin = new Admin();
        admin.setId(42L);
        admin.setRole(ActorRole.ROLE_ADMIN);
        when(admins.findLockedById(42L)).thenReturn(Optional.of(admin));
        when(duplicates.pending(42L, null, 25)).thenReturn(new CandidatePage(java.util.List.of(), null));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("authenticatedUserRole", ActorRole.ROLE_ADMIN.name());
        session.setAttribute("authenticatedUserId", 42L);
        session.setAttribute("adminCredentialVersion", 0L);

        mvc().perform(get("/api/admin/applications/possible-duplicates").session(session))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Cache-Control", "no-store"));

        verify(duplicates).pending(42L, null, 25);
    }

    @Test
    void applicationListRequiresAdministrator() throws Exception {
        mvc().perform(get("/api/admin/applications").param("status", "Pending"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(results);
    }

    private org.springframework.test.web.servlet.MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(controller).build();
    }
}
