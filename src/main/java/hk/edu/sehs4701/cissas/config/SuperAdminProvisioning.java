package hk.edu.sehs4701.cissas.config;

import hk.edu.sehs4701.cissas.dto.request.AccountRequest;
import hk.edu.sehs4701.cissas.service.AdminAccountService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import java.util.Arrays;

@Component
public class SuperAdminProvisioning implements ApplicationRunner {
    private final AdminAccountService accounts;
    private final Environment environment;

    public SuperAdminProvisioning(AdminAccountService accounts, Environment environment) {
        this.accounts = accounts;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (Arrays.asList(args.getSourceArgs()).contains("--recover-super-admin")) {
            accounts.recoverSuperAdmin(environment.getProperty("CISSAS_RECOVERY_EMAIL", ""),
                    environment.getProperty("CISSAS_RECOVERY_PASSWORD", ""));
            return;
        }
        accounts.provisionSuperAdmin(new AccountRequest(
                environment.getProperty("CISSAS_SUPER_ADMIN_EMAIL", ""),
                environment.getProperty("CISSAS_SUPER_ADMIN_FIRST_NAME", ""),
                environment.getProperty("CISSAS_SUPER_ADMIN_LAST_NAME", ""),
                environment.getProperty("CISSAS_SUPER_ADMIN_PASSWORD", "")));
    }
}
