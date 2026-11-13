package hk.edu.sehs4701.cissas.dto.request;

import hk.edu.sehs4701.cissas.enums.IdentityDocumentType;
import hk.edu.sehs4701.cissas.enums.LevelCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public record ApplicationSubmissionRequest(
        @NotNull
        LevelCode levelCode,
        @NotBlank @Size(max = 120)
        String applicantFullName,
        @NotBlank @Size(max = 512)
        String applicantIdentityDocumentNumber,
        @NotNull
        IdentityDocumentType applicantIdentityDocumentType,
        @Size(max = 2)
        String applicantIdentityDocumentIssuingCountry,
        @NotNull
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate dateOfBirth,
        @NotBlank @Size(max = 20)
        String gender,
        @NotBlank @Size(max = 25)
        String phone,
        @NotBlank @Size(max = 150)
        String previousSchool
) {}
