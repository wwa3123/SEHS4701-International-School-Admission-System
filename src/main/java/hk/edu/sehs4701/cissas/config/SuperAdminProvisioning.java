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
            accounts.recoverSuperAdmin(environment.getProperty("cissas.recovery.email", ""),
                    environment.getProperty("cissas.recovery.password", ""));
            return;
        }
        accounts.provisionSuperAdmin(new AccountRequest(
                environment.getProperty("cissas.super-admin.email", ""),
                environment.getProperty("cissas.super-admin.first-name", ""),
                environment.getProperty("cissas.super-admin.last-name", ""),
                environment.getProperty("cissas.super-admin.password", "")));
    }
}
