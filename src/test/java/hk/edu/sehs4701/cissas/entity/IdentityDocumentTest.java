package hk.edu.sehs4701.cissas.entity;

import hk.edu.sehs4701.cissas.enums.IdentityDocumentType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IdentityDocumentTest {
    @Test
    void cannotReassignOrAttachDocumentToAnotherMembersApplicant() {
        Member owner = new Member();
        owner.setId(1L);
        Member other = new Member();
        other.setId(2L);
        IdentityDocument document = new IdentityDocument(
                IdentityDocumentType.HKID, "A123456(3)", "HK", "hash");
        owner.addIdentityDocument(document);

        Applicant otherApplicant = new Applicant();
        otherApplicant.setMember(other);
        assertThrows(IllegalStateException.class, () -> otherApplicant.addIdentityDocument(document));

        Applicant ownerApplicant = new Applicant();
        ownerApplicant.setMember(owner);
        ownerApplicant.addIdentityDocument(document);
        assertSame(ownerApplicant, document.getApplicant());
        assertThrows(IllegalStateException.class, () -> ownerApplicant.addIdentityDocument(document));
        assertThrows(IllegalStateException.class, () -> document.attachToMember(other));
    }
}
