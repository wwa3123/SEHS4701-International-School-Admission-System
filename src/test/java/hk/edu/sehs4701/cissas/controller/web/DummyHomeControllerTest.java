package hk.edu.sehs4701.cissas.controller.web;

import hk.edu.sehs4701.cissas.dto.response.AcademicYearResponse;
import hk.edu.sehs4701.cissas.enums.AcademicYearStatus;
import hk.edu.sehs4701.cissas.service.AcademicYearService;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DummyHomeControllerTest {
    private final AcademicYearService academicYearService = mock(AcademicYearService.class);
    private final DummyHomeController controller = new DummyHomeController(academicYearService);

    @Test
    void addsAcademicYearsToHomePageModel() {
        List<AcademicYearResponse> academicYears = List.of(new AcademicYearResponse(
                1L,
                "2026/27",
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2027, 8, 31),
                AcademicYearStatus.ACTIVE
        ));
        when(academicYearService.list()).thenReturn(academicYears);
        ExtendedModelMap model = new ExtendedModelMap();

        assertEquals("index", controller.index(model));
        assertEquals(academicYears, model.getAttribute("academicYears"));
    }
}
