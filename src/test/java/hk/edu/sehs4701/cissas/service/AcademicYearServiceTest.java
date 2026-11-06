package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.dto.response.AcademicYearResponse;
import hk.edu.sehs4701.cissas.entity.AcademicYear;
import hk.edu.sehs4701.cissas.enums.AcademicYearStatus;
import hk.edu.sehs4701.cissas.repository.AcademicYearRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class AcademicYearServiceTest {
    private final AcademicYearRepository repository = mock(AcademicYearRepository.class);
    private final AcademicYearService service = new AcademicYearService(repository);

    @Test
    void listsAcademicYearsAsResponses() {
        AcademicYear academicYear = academicYear(1L, "2026/27");
        when(repository.findAllByOrderByApplicationStartDateDesc()).thenReturn(List.of(academicYear));

        assertEquals(
                List.of(new AcademicYearResponse(
                        1L, "2026/27", LocalDate.of(2026, 9, 1), LocalDate.of(2027, 8, 31),
                        AcademicYearStatus.OPEN_FOR_ADMISSION
                )),
                service.list()
        );
    }

    @Test
    void getsAcademicYearById() {
        when(repository.findById(1L)).thenReturn(Optional.of(academicYear(1L, "2026/27")));

        assertEquals(
                new AcademicYearResponse(
                        1L, "2026/27", LocalDate.of(2026, 9, 1), LocalDate.of(2027, 8, 31),
                        AcademicYearStatus.OPEN_FOR_ADMISSION
                ),
                service.get(1L)
        );
    }

    @Test
    void getsCurrentOpenForApplicationAcademicYear() {
        AcademicYear activeYear = academicYear(1L, "2026/27");
        activeYear.setStatus(AcademicYearStatus.OPEN_FOR_ADMISSION);
        when(repository.findFirstByStatusOrderByApplicationStartDateDesc(AcademicYearStatus.OPEN_FOR_ADMISSION))
                .thenReturn(Optional.of(activeYear));

        assertEquals(
                new AcademicYearResponse(
                        1L, "2026/27", LocalDate.of(2026, 9, 1), LocalDate.of(2027, 8, 31),
                        AcademicYearStatus.OPEN_FOR_ADMISSION
                ),
                service.getCurrentOpenForApplicationAcademicYear()
        );
    }

    @Test
    void returnsNotFoundWhenThereIsNoAcademicYearOpenForApplications() {
        when(repository.findFirstByStatusOrderByApplicationStartDateDesc(AcademicYearStatus.OPEN_FOR_ADMISSION))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class, service::getCurrentOpenForApplicationAcademicYear
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void returnsNotFoundForUnknownId() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class, () -> service.get(99L)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    private AcademicYear academicYear(Long id, String name) {
        AcademicYear academicYear = new AcademicYear();
        academicYear.setId(id);
        academicYear.setName(name);
        academicYear.setApplicationStartDate(LocalDate.of(2026, 9, 1));
        academicYear.setApplicationEndDate(LocalDate.of(2027, 8, 31));
        academicYear.setStatus(AcademicYearStatus.OPEN_FOR_ADMISSION);
        return academicYear;
    }
}
