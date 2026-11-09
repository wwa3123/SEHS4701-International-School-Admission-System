package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.dto.response.AcademicYearResponse;
import hk.edu.sehs4701.cissas.entity.AcademicYear;
import hk.edu.sehs4701.cissas.enums.AcademicYearStatus;
import hk.edu.sehs4701.cissas.repository.AcademicYearRepository;
import hk.edu.sehs4701.cissas.repository.IdentityReviewLockRepository;
import hk.edu.sehs4701.cissas.repository.ProgrammeLevelRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class AcademicYearServiceTest {
    private final AcademicYearRepository repository = mock(AcademicYearRepository.class);
    private final IdentityReviewLockRepository lock = mock(IdentityReviewLockRepository.class);
    private final ProgrammeLevelRepository levels = mock(ProgrammeLevelRepository.class);
    private final AcademicYearService service = new AcademicYearService(repository, lock, levels);

    @Test
    void listsAcademicYearsAsResponses() {
        AcademicYear academicYear = academicYear(1L, "2026/27");
        when(repository.findAllByOrderByCreationDateDesc()).thenReturn(List.of(academicYear));

        assertEquals(
                List.of(new AcademicYearResponse(
                        1L, "2026/27", LocalDate.of(2026, 9, 1), null,
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
                        1L, "2026/27", LocalDate.of(2026, 9, 1), null,
                        AcademicYearStatus.ACTIVE
                ),
                service.get(1L)
        );
    }

    @Test
    void getsCurrentOpenForApplicationAcademicYearWhenAnyLevelIsOpen() {
        AcademicYear activeYear = academicYear(1L, "2026/27");
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Hong_Kong"));
        when(repository.findFirstByStatusOrderByCreationDateDesc(AcademicYearStatus.ACTIVE))
                .thenReturn(Optional.of(activeYear));
        when(levels.existsByAcademicYear_IdAndApplicationStartDateLessThanEqualAndApplicationEndDateGreaterThanEqual(
                1L, today, today)).thenReturn(true);

        assertEquals(
                new AcademicYearResponse(
                        1L, "2026/27", LocalDate.of(2026, 9, 1), null,
                        AcademicYearStatus.ACTIVE
                ),
                service.getCurrentOpenForApplicationAcademicYear()
        );
    }

    @Test
    void openYearRequiresAtLeastOneProgrammeLevelOpenToday() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Hong_Kong"));
        AcademicYear year = academicYear(1L, "test");
        when(repository.findFirstByStatusOrderByCreationDateDesc(AcademicYearStatus.ACTIVE))
                .thenReturn(Optional.of(year));

        when(levels.existsByAcademicYear_IdAndApplicationStartDateLessThanEqualAndApplicationEndDateGreaterThanEqual(
                1L, today, today)).thenReturn(false, true, true, false);
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                service::getCurrentOpenForApplicationAcademicYear).getStatusCode());
        assertEquals(1L, service.getCurrentOpenForApplicationAcademicYear().id());
        assertEquals(1L, service.getCurrentOpenForApplicationAcademicYear().id());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                service::getCurrentOpenForApplicationAcademicYear).getStatusCode());
    }

    @Test
    void returnsNotFoundWhenThereIsNoAcademicYearOpenForApplications() {
        when(repository.findFirstByStatusOrderByCreationDateDesc(AcademicYearStatus.ACTIVE))
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
                        5L, "2028/29", LocalDate.now(ZoneId.of("Asia/Hong_Kong")), null,
                        AcademicYearStatus.PLANNING),
                service.create(" 2028/29 "));
        verify(repository).save(argThat(year -> year.getStatus() == AcademicYearStatus.PLANNING
                && year.getCreationDate().equals(LocalDate.now(ZoneId.of("Asia/Hong_Kong")))));
    }

    @Test
    void rejectsDuplicateAcademicYearNames() {
        when(repository.existsByName("2028/29")).thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.create("2028/29"));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(repository, never()).save(any());
    }

    @Test
    void changesAcademicYearLifecycle() {
        AcademicYear year = academicYear(1L, "2026/27");
        when(repository.findById(1L)).thenReturn(Optional.of(year));
        when(repository.save(year)).thenReturn(year);

        assertEquals(AcademicYearStatus.ARCHIVED,
                service.updateLifecycle(1L, AcademicYearStatus.ARCHIVED).status());
        assertEquals(AcademicYearStatus.ARCHIVED, year.getStatus());
        assertEquals(LocalDate.now(ZoneId.of("Asia/Hong_Kong")), year.getArchiveDate());
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
        assertEquals(LocalDate.now(ZoneId.of("Asia/Hong_Kong")), year.getArchiveDate());
        service.updateLifecycle(1L, AcademicYearStatus.ACTIVE);
        assertNull(year.getArchiveDate());
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
        academicYear.setCreationDate(LocalDate.of(2026, 9, 1));
        academicYear.setStatus(AcademicYearStatus.ACTIVE);
        return academicYear;
    }
}
