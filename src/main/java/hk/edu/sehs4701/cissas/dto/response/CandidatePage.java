package hk.edu.sehs4701.cissas.dto.response;

import java.util.List;

public record CandidatePage(List<Candidate> items, String nextCursor) {
}
