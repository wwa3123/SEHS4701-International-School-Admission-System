package hk.edu.sehs4701.cissas.config;

import hk.edu.sehs4701.cissas.service.AdminAccountService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

import static org.mockito.Mockito.*;

class SuperAdminProvisioningTest {
    @Test
    void recoveryRunsOnlyForExplicitLocalCommand() {
        AdminAccountService accounts = mock(AdminAccountService.class);
        MockEnvironment environment = new MockEnvironment()
                .withProperty("CISSAS_RECOVERY_EMAIL", "super@example.com")
                .withProperty("CISSAS_RECOVERY_PASSWORD", "temporary-password");
        SuperAdminProvisioning runner = new SuperAdminProvisioning(accounts, environment);
        runner.run(new DefaultApplicationArguments("--recover-super-admin"));
        verify(accounts).recoverSuperAdmin("super@example.com", "temporary-password");
        verify(accounts, never()).provisionSuperAdmin(any());
        reset(accounts);
        runner.run(new DefaultApplicationArguments("--recover-super-admin=true"));
        verify(accounts, never()).recoverSuperAdmin(any(), any());
        verify(accounts).provisionSuperAdmin(any());
    }
}
