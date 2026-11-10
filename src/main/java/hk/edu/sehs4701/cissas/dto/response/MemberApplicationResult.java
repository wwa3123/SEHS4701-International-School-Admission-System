package hk.edu.sehs4701.cissas.dto.response;

import hk.edu.sehs4701.cissas.enums.ApplicationStatus;

import java.time.LocalDateTime;

public record MemberApplicationResult(String applicationNumber, String programmeLevel,
                                      ApplicationStatus status, LocalDateTime resultReleasedAt,
                                      LocalDateTime acceptanceDeadline) {
}
