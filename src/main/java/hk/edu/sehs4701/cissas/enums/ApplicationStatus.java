package hk.edu.sehs4701.cissas.enums;

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