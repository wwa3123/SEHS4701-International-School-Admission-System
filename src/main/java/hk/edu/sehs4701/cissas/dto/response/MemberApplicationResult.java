package hk.edu.sehs4701.cissas.dto.response;

import hk.edu.sehs4701.cissas.enums.ActorRole;
import hk.edu.sehs4701.cissas.enums.ApplicationStatus;
import hk.edu.sehs4701.cissas.enums.IdentityDocumentType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record MemberApplicationResult(String applicationNumber, String academicYear, String programmeLevel,
                                      String applicantName, LocalDate dateOfBirth, String gender, String phone,
                                      String previousSchool, IdentityDocumentType identityDocumentType,
                                      String identityDocumentNumber, String issuingCountry,
                                      ApplicationStatus status, LocalDateTime submittedAt,
                                      LocalDateTime declarationsAcceptedAt, LocalDateTime resultReleasedAt,
                                      LocalDateTime acceptanceDeadline, boolean cancellationAllowed,
                                      List<StatusChange> statusHistory) {
    public record StatusChange(ApplicationStatus fromStatus, ApplicationStatus toStatus, ActorRole changedByRole,
                               String remarks, LocalDateTime changedAt) {
    }
}
