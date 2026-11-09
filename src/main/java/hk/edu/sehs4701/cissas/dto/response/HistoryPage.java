package hk.edu.sehs4701.cissas.dto.response;

import java.util.List;

public record HistoryPage(List<Decision> items, String nextCursor) {
}
