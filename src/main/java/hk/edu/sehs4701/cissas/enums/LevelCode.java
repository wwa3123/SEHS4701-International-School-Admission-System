package hk.edu.sehs4701.cissas.enums;

public enum LevelCode {
    S1, S2, S3, S4, S5, S6;

    public String getProgrammeName() {
        return this == S5 || this == S6 ? "IB DP" : "IB MYP";
    }

    public String getDisplayName() {
        return name() + " - " + getProgrammeName();
    }
}