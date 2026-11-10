package hk.edu.sehs4701.cissas.dto.response;

import hk.edu.sehs4701.cissas.enums.ApplicationStatus;

public record AdminApplication(String applicationNumber, Long programmeLevelId, String programmeLevel,
                               String applicantName, ApplicationStatus status) {
}
