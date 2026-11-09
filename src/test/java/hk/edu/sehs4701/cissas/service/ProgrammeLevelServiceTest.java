package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.dto.response.ProgrammeLevelResponse;
import hk.edu.sehs4701.cissas.entity.AcademicYear;
import hk.edu.sehs4701.cissas.entity.ProgrammeLevel;
import hk.edu.sehs4701.cissas.enums.LevelCode;
import hk.edu.sehs4701.cissas.repository.AcademicYearRepository;
import hk.edu.sehs4701.cissas.repository.IdentityReviewLockRepository;
import hk.edu.sehs4701.cissas.repository.ProgrammeLevelRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProgrammeLevelServiceTest {
    private final ProgrammeLevelRepository levels = mock(ProgrammeLevelRepository.class);
    private final AcademicYearRepository years = mock(AcademicYearRepository.class);
    private final IdentityReviewLockRepository lock = mock(IdentityReviewLockRepository.class);
    private final ProgrammeLevelService service = new ProgrammeLevelService(levels, years, lock);
    private final LocalDate applicationStartDate = LocalDate.of(2028, 9, 1);
    private final LocalDate applicationEndDate = LocalDate.of(2028, 9, 30);

    private AcademicYear year() {
        AcademicYear year = new AcademicYear();
        year.setId(1L);
        return year;
    }

    @Test
    void createsYearSpecificProgramme() {
        AcademicYear year = year();
        when(years.findById(1L)).thenReturn(Optional.of(year));
        when(levels.save(any())).thenAnswer(invocation -> {
            ProgrammeLevel level = invocation.getArgument(0);
            level.setId(10L);
            return level;
        });
        assertEquals(new ProgrammeLevelResponse(10L, 1L, LevelCode.S1, "Secondary", 150,
                        applicationStartDate, applicationEndDate),
                service.create(1L, LevelCode.S1, " Secondary ", 150,
                        applicationStartDate, applicationEndDate));
        verify(lock).acquire();
        verify(levels).save(argThat(level -> level.getAcademicYear() == year));
    }

    @Test
    void rejectsDuplicateAndInvalidProgrammeApplicationPeriod() {
        when(years.findById(1L)).thenReturn(Optional.of(year()));
        when(levels.existsByLevelCodeAndAcademicYear_Id(LevelCode.S1, 1L)).thenReturn(true);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.create(1L, LevelCode.S1, "Secondary", 150,
                        applicationStartDate, applicationEndDate)).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.create(1L, LevelCode.S1, "Secondary", 150,
                        applicationEndDate, applicationStartDate)).getStatusCode());
        verify(levels, never()).save(any());
    }

    @Test
    void updatesOnlyTheRequestedYearsProgrammeAndValidatesDates() {
        ProgrammeLevel level = new ProgrammeLevel();
        level.setId(10L);
        level.setAcademicYear(year());
        level.setLevelCode(LevelCode.S1);
        level.setApplicationStartDate(applicationStartDate);
        level.setApplicationEndDate(applicationEndDate);
        when(levels.findByIdAndAcademicYear_Id(10L, 1L)).thenReturn(Optional.of(level));
        when(levels.save(level)).thenReturn(level);
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.update(2L, 10L, "Corrected", 100,
                        applicationStartDate, applicationEndDate)).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.update(1L, 10L, "Corrected", 100,
                        applicationEndDate, applicationStartDate)).getStatusCode());
        assertEquals(new ProgrammeLevelResponse(10L, 1L, LevelCode.S1, "Corrected", 100,
                        applicationStartDate, applicationEndDate),
                service.update(1L, 10L, " Corrected ", 100,
                        applicationStartDate, applicationEndDate));
        verify(levels, times(1)).save(level);
    }

    @Test
    void listsLevelsForExistingYear() {
        ProgrammeLevel level = new ProgrammeLevel();
        level.setId(10L);
        level.setAcademicYear(year());
        level.setLevelCode(LevelCode.S1);
        level.setProgrammeName("Secondary");
        level.setApplicationStartDate(applicationStartDate);
        level.setApplicationEndDate(applicationEndDate);
        when(years.existsById(1L)).thenReturn(true);
        when(levels.findAllByAcademicYear_IdOrderByLevelCodeAsc(1L)).thenReturn(List.of(level));
        assertEquals(List.of(new ProgrammeLevelResponse(10L, 1L, LevelCode.S1, "Secondary", 150,
                        applicationStartDate, applicationEndDate)),
                service.list(1L));
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.list(2L)).getStatusCode());
    }
}
