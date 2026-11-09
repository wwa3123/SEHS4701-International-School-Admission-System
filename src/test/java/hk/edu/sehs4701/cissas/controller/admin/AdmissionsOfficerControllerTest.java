package hk.edu.sehs4701.cissas.controller.admin;

import hk.edu.sehs4701.cissas.controller.api.admin.AdmissionsOfficerController;
import hk.edu.sehs4701.cissas.entity.Admin;
import hk.edu.sehs4701.cissas.enums.ActorRole;
import hk.edu.sehs4701.cissas.repository.AdminRepository;
import hk.edu.sehs4701.cissas.repository.IdentityReviewLockRepository;
import hk.edu.sehs4701.cissas.security.HashService;
import hk.edu.sehs4701.cissas.service.AdminAccountService;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdmissionsOfficerControllerTest {
    private final AdminRepository admins = mock(AdminRepository.class);
    private final HashService hash = mock(HashService.class);
    private final AdminAccountService accounts = new AdminAccountService(admins,
            mock(IdentityReviewLockRepository.class), hash, Validation.buildDefaultValidatorFactory().getValidator());
    private final AdmissionsOfficerController controller = new AdmissionsOfficerController(accounts);
    private final String body = """
            {"email":"officer@example.com","firstName":"First","lastName":"Last","password":"password123"}
            """;

    @Test
    void onlySuperAdminsCanCreateAndDeactivateOfficers() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("authenticatedUserId", 1L);
        session.setAttribute("adminCredentialVersion", 0L);
        session.setAttribute("authenticatedUserRole", "ROLE_ADMIN");
        mvc.perform(post("/api/super-admin/admissions-officers").session(session)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(get("/api/super-admin/admissions-officers").session(session))
                .andExpect(status().isForbidden());

        Admin superAdmin = new Admin();
        superAdmin.setRole(ActorRole.ROLE_SUPER_ADMIN);
        when(admins.findById(1L)).thenReturn(Optional.of(superAdmin));
        when(admins.findLockedById(1L)).thenReturn(Optional.of(superAdmin));
        session.setAttribute("authenticatedUserRole", "ROLE_SUPER_ADMIN");
        Admin listed = new Admin();
        listed.setId(42L);
        listed.setEmail("officer@example.com");
        when(admins.findAllByRoleOrderByEmailAsc(ActorRole.ROLE_ADMIN)).thenReturn(List.of(listed));
        mvc.perform(get("/api/super-admin/admissions-officers").session(session))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$[0].id").value("42"))
                .andExpect(jsonPath("$[0].email").value("officer@example.com"));
        verify(admins, times(3)).findLockedById(1L);
        when(hash.hashPassword("password123")).thenReturn("hash");
        when(admins.save(any())).thenAnswer(invocation -> {
            Admin admin = invocation.getArgument(0);
            if (admin.getId() == null) admin.setId(42L);
            return admin;
        });
        mvc.perform(post("/api/super-admin/admissions-officers").session(session)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("42"))
                .andExpect(jsonPath("$.active").value(true));

        Admin officer = new Admin();
        officer.setId(42L);
        when(admins.findLockedById(42L)).thenReturn(Optional.of(officer));
        mvc.perform(delete("/api/super-admin/admissions-officers/42").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        superAdmin.setActive(false);
        mvc.perform(delete("/api/super-admin/admissions-officers/42").session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidPasswordIsRejectedWithoutSpringValidationException() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("authenticatedUserId", 1L);
        session.setAttribute("adminCredentialVersion", 0L);
        session.setAttribute("authenticatedUserRole", "ROLE_SUPER_ADMIN");
        Admin admin = new Admin();
        admin.setRole(ActorRole.ROLE_SUPER_ADMIN);
        when(admins.findById(1L)).thenReturn(Optional.of(admin));
        when(admins.findLockedById(1L)).thenReturn(Optional.of(admin));

        var result = mvc.perform(post("/api/super-admin/admissions-officers").session(session)
                .contentType(MediaType.APPLICATION_JSON).content(body.replace("password123", "sh0rt")))
                .andExpect(status().isBadRequest()).andReturn();
        org.junit.jupiter.api.Assertions.assertInstanceOf(
                org.springframework.web.server.ResponseStatusException.class, result.getResolvedException());
        org.junit.jupiter.api.Assertions.assertFalse(result.getResponse().getContentAsString().contains("sh0rt"));
        verify(admins, never()).save(any());
    }

    @Test
    void onlySuperAdminCanResetOfficerPassword() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("authenticatedUserId", 1L);
        session.setAttribute("authenticatedUserRole", "ROLE_ADMIN");
        session.setAttribute("adminCredentialVersion", 0L);
        String reset = "{\"newPassword\":\"temporary-password\"}";
        mvc.perform(put("/api/super-admin/admissions-officers/42/password").session(session)
                .contentType(MediaType.APPLICATION_JSON).content(reset)).andExpect(status().isForbidden());
        Admin superAdmin = new Admin();
        superAdmin.setRole(ActorRole.ROLE_SUPER_ADMIN);
        when(admins.findById(1L)).thenReturn(Optional.of(superAdmin));
        when(admins.findLockedById(1L)).thenReturn(Optional.of(superAdmin));
        Admin officer = new Admin();
        when(admins.findLockedById(42L)).thenReturn(Optional.of(officer));
        when(hash.hashPassword("temporary-password")).thenReturn("hash");
        session.setAttribute("authenticatedUserRole", "ROLE_SUPER_ADMIN");
        mvc.perform(put("/api/super-admin/admissions-officers/42/password").session(session)
                .contentType(MediaType.APPLICATION_JSON).content(reset)).andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertTrue(officer.isPasswordChangeRequired());
        org.junit.jupiter.api.Assertions.assertEquals(1, officer.getCredentialVersion());
    }
}
