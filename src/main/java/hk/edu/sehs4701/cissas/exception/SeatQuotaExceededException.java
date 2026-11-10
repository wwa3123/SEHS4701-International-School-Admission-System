package hk.edu.sehs4701.cissas.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class SeatQuotaExceededException extends ResponseStatusException {
    public SeatQuotaExceededException(long count) {
        super(HttpStatus.CONFLICT, "Seat quota cannot be lower than the current Success and Accepted count: " + count);
    }
}
