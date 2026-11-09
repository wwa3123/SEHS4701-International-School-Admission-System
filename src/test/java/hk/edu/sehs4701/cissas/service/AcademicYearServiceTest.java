package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.dto.response.AcademicYearResponse;
import hk.edu.sehs4701.cissas.entity.AcademicYear;
import hk.edu.sehs4701.cissas.enums.AcademicYearStatus;
import hk.edu.sehs4701.cissas.repository.AcademicYearRepository;
import hk.edu.sehs4701.cissas.repository.IdentityReviewLockRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class AcademicYearServiceTest {
    private final AcademicYearRepository repository = mock(AcademicYearRepository.class);
    private final IdentityReviewLockRepository lock = mock(IdentityReviewLockRepository.class);
    private final AcademicYearService service = new AcademicYearService(repository, lock);

    @Test
    void listsAcademicYearsAsResponses() {
        AcademicYear academicYear = academicYear(1L, "2026/27");
        when(repository.findAllByOrderByApplicationStartDateDesc()).thenReturn(List.of(academicYear));

        assertEquals(
                List.of(new AcademicYearResponse(
                        1L, "2026/27", LocalDate.of(2026, 9, 1), LocalDate.of(2027, 8, 31),
                        AcademicYearStatus.ACTIVE
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
                        AcademicYearStatus.ACTIVE
                ),
                service.get(1L)
        );
    }

    @Test
    void getsCurrentOpenForApplicationAcademicYear() {
        AcademicYear activeYear = academicYear(1L, "2026/27");
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Hong_Kong"));
        activeYear.setApplicationStartDate(today.minusDays(1));
        activeYear.setApplicationEndDate(today.plusDays(1));
        when(repository.findFirstByStatusOrderByApplicationStartDateDesc(AcademicYearStatus.ACTIVE))
                .thenReturn(Optional.of(activeYear));

        assertEquals(
                new AcademicYearResponse(
                        1L, "2026/27", today.minusDays(1), today.plusDays(1),
                        AcademicYearStatus.ACTIVE
                ),
                service.getCurrentOpenForApplicationAcademicYear()
        );
    }

    @Test
    void openYearRequiresTodayWithinInclusiveHongKongApplicationDates() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Hong_Kong"));
        AcademicYear year = academicYear(1L, "test");
        when(repository.findFirstByStatusOrderByApplicationStartDateDesc(AcademicYearStatus.ACTIVE))
                .thenReturn(Optional.of(year));

        year.setApplicationStartDate(today.plusDays(1));
        year.setApplicationEndDate(today.plusDays(2));
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                service::getCurrentOpenForApplicationAcademicYear).getStatusCode());

        year.setApplicationStartDate(today);
        assertEquals(1L, service.getCurrentOpenForApplicationAcademicYear().id());
        year.setApplicationStartDate(today.minusDays(1));
        year.setApplicationEndDate(today);
        assertEquals(1L, service.getCurrentOpenForApplicationAcademicYear().id());

        year.setApplicationEndDate(today.minusDays(1));
        year.setApplicationStartDate(today.minusDays(2));
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                service::getCurrentOpenForApplicationAcademicYear).getStatusCode());
    }

    @Test
    void returnsNotFoundWhenThereIsNoAcademicYearOpenForApplications() {
        when(repository.findFirstByStatusOrderByApplicationStartDateDesc(AcademicYearStatus.ACTIVE))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class, service::getCurrentOpenForApplicationAcademicYear
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void createsAcademicYearsInPlanningLifecycle() {
        when(repository.save(any(AcademicYear.class))).thenAnswer(invocation -> {
            AcademicYear year = invocation.getArgument(0);
            year.setId(5L);
            return year;
        });

        assertEquals(new AcademicYearResponse(
                        5L, "2028/29", LocalDate.of(2028, 9, 1), LocalDate.of(2029, 8, 31),
                        AcademicYearStatus.PLANNING),
                service.create(" 2028/29 ", LocalDate.of(2028, 9, 1), LocalDate.of(2029, 8, 31)));
        verify(repository).save(argThat(year -> year.getStatus() == AcademicYearStatus.PLANNING));
    }

    @Test
    void rejectsDuplicateAcademicYearNames() {
        when(repository.existsByName("2028/29")).thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.create("2028/29", LocalDate.of(2028, 9, 1), LocalDate.of(2029, 8, 31)));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsInvalidApplicationDateRange() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.create("2028/29", LocalDate.of(2029, 9, 1), LocalDate.of(2028, 8, 31)));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(repository);
    }

    @Test
    void changesAcademicYearLifecycle() {
        AcademicYear year = academicYear(1L, "2026/27");
        when(repository.findById(1L)).thenReturn(Optional.of(year));
        when(repository.save(year)).thenReturn(year);

        assertEquals(AcademicYearStatus.ARCHIVED,
                service.updateLifecycle(1L, AcademicYearStatus.ARCHIVED).status());
        assertEquals(AcademicYearStatus.ARCHIVED, year.getStatus());
    }

    @Test
    void rejectsMissingLifecycleValue() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.updateLifecycle(1L, null));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(repository);
    }

    @Test
    void acceptsEachSupportedLifecycleValue() {
        AcademicYear year = academicYear(1L, "2026/27");
        when(repository.findById(1L)).thenReturn(Optional.of(year));
        when(repository.save(year)).thenReturn(year);
        for (AcademicYearStatus status : List.of(
                AcademicYearStatus.PLANNING, AcademicYearStatus.ACTIVE, AcademicYearStatus.ARCHIVED)) {
            assertEquals(status, service.updateLifecycle(1L, status).status());
            assertEquals(status, year.getStatus());
        }
    }

    @Test
    void rejectsActivationWhenAnotherYearIsActive() {
        AcademicYear year = academicYear(1L, "2026/27");
        year.setStatus(AcademicYearStatus.PLANNING);
        when(repository.findById(1L)).thenReturn(Optional.of(year));
        when(repository.existsByStatusAndIdNot(AcademicYearStatus.ACTIVE, 1L)).thenReturn(true);

        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.updateLifecycle(1L, AcademicYearStatus.ACTIVE)).getStatusCode());
        assertEquals(AcademicYearStatus.PLANNING, year.getStatus());
        verify(lock).acquire();
        verify(repository, never()).save(any());
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
        academicYear.setStatus(AcademicYearStatus.ACTIVE);
        return academicYear;
    }
}
