package hk.edu.sehs4701.cissas.event;

public record EmailSendRequestedEvent(Long emailLogId, String transientBody) {
}
