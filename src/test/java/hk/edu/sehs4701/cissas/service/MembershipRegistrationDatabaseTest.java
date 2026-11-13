package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.controller.web.MembershipPageController;
import hk.edu.sehs4701.cissas.dto.request.MembershipEmailRequest;
import hk.edu.sehs4701.cissas.dto.request.MembershipRegistrationRequest;
import hk.edu.sehs4701.cissas.dto.request.VerifyMembershipEmailRequest;
import hk.edu.sehs4701.cissas.entity.Member;
import hk.edu.sehs4701.cissas.enums.AccountStatus;
import hk.edu.sehs4701.cissas.enums.IdentityDocumentType;
import hk.edu.sehs4701.cissas.repository.IdentityDocumentRepository;
import hk.edu.sehs4701.cissas.repository.MemberRepository;
import hk.edu.sehs4701.cissas.repository.OneTimeCodeRepository;
import hk.edu.sehs4701.cissas.security.AesEncryptionConverter;
import hk.edu.sehs4701.cissas.security.HashService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = MembershipRegistrationDatabaseTest.Config.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "identity-document.encryption.secret=test-only-secret",
        "identity-document.encryption.salt=0123456789abcdef",
        "identity-document.hash.pepper=test-only-pepper",
        "otc.code.hash.pepper=test-only-pepper"
})
@EnabledIfEnvironmentVariable(named = "CISSAS_REGISTRATION_TEST_DATABASE_URL", matches = ".+")
class MembershipRegistrationDatabaseTest {
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = Member.class)
    @EnableJpaRepositories(basePackageClasses = MemberRepository.class)
    @Import({MembershipRegistrationService.class, OtcService.class, IdentityDocumentService.class,
            HashService.class, AesEncryptionConverter.class})
    static class Config {}

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String url = System.getenv("CISSAS_REGISTRATION_TEST_DATABASE_URL");
        if (url == null || !url.matches("jdbc:mysql://localhost:3306/cissas_registration_test_[a-z0-9]+")) {
            throw new IllegalStateException("A disposable local cissas_registration_test_* database is required");
        }
        registry.add("spring.datasource.url", () -> url + "?createDatabaseIfNotExist=true");
    }

    @MockitoBean private NotificationService notifications;
    @Autowired private MembershipRegistrationService registration;
    @Autowired private MemberRepository members;
    @Autowired private OneTimeCodeRepository codes;
    @Autowired private IdentityDocumentRepository documents;
    @Autowired private IdentityDocumentService documentService;
    @Autowired private HashService hashes;
    @Autowired private TransactionTemplate transactions;

    @Test
    void emailVerificationAndCompletionPersistOnlyOwnerDetails() {
        var controller = new MembershipPageController(registration, mock(RateLimitService.class));
        var browser = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        var model = new ExtendedModelMap();
        var redirect = new RedirectAttributesModelMap();
        var email = new MembershipEmailRequest("owner@example.com");
        controller.register(email, new BeanPropertyBindingResult(email, "emailForm"), browser, response, model, redirect);

        Member pending = members.findFirstByEmailIgnoreCase(email.email()).orElseThrow();
        assertNull(pending.getPasswordHash());
        assertNull(pending.getFirstName());
        assertEquals(AccountStatus.UNVERIFIED, pending.getAccountStatus());

        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(notifications).sendVerificationCode(any(Member.class), code.capture(), anyInt());
        var wrongCode = new VerifyMembershipEmailRequest(email.email(), "000000");
        assertEquals("auth/registration-verify", controller.verify(wrongCode,
                new BeanPropertyBindingResult(wrongCode, "verificationForm"), browser, response, model));
        assertEquals(400, response.getStatus());
        Integer attempts = transactions.execute(status ->
                codes.findFirstByMemberIdOrderByCreatedAtDesc(pending.getId()).orElseThrow().getAttemptCount());
        assertEquals(1, attempts);
        var verification = new VerifyMembershipEmailRequest(email.email(), code.getValue());
        assertEquals("redirect:/membership/complete-registration", controller.verify(verification,
                new BeanPropertyBindingResult(verification, "verificationForm"), browser, response, model));
        assertEquals(AccountStatus.UNVERIFIED, members.findFirstByEmailIgnoreCase(email.email()).orElseThrow().getAccountStatus());

        var details = new MembershipRegistrationRequest("Owner", "Name", email.email(), "12345678",
                IdentityDocumentType.HKID, "A123456(3)", null, "secure-password");
        var errors = new BeanPropertyBindingResult(details, "registrationForm");
        assertEquals("auth/registration-verify", controller.complete(details, errors, new MockHttpServletRequest(),
                response, model, redirect));
        assertEquals(403, response.getStatus());
        assertEquals("redirect:/membership/success", controller.complete(details, errors, browser, response, model, redirect));
        Member active = members.findFirstByEmailIgnoreCase(email.email()).orElseThrow();
        assertEquals(AccountStatus.ACTIVE, active.getAccountStatus());
        assertEquals("Owner", active.getFirstName());
        assertTrue(hashes.verifyPassword("secure-password", active.getPasswordHash()));
        assertNotNull(active.getEmailVerifiedAt());
        String documentHash = documentService.getDocumentNumberHash(IdentityDocumentType.HKID, "A123456(3)", null);
        transactions.executeWithoutResult(status -> {
            var document = documents.findByDocumentNumberHash(documentHash).orElseThrow();
            assertEquals(active.getId(), document.getMember().getId());
            assertEquals("A123456(3)", document.getDocumentNumber());
        });
        assertEquals("auth/registration-verify", controller.complete(details, errors, browser, response, model, redirect));
        assertEquals(403, response.getStatus());
        assertEquals("auth/registration-verify", controller.verify(verification,
                new BeanPropertyBindingResult(verification, "verificationForm"), new MockHttpServletRequest(), response, model));
        assertEquals(400, response.getStatus());
    }
}
