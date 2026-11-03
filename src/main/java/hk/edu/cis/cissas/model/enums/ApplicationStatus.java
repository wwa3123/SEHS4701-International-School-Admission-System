package hk.edu.cis.cissas.model.enums;

public enum ApplicationStatus {
    Pending,
    Cancelled,
    Success,
    Wait,
    Fail,
    Accepted,
    Rejected;

    public boolean isFinal() {
        return this == Cancelled || this == Accepted || this == Rejected;
    }
}