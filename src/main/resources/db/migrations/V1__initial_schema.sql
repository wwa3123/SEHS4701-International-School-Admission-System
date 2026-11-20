CREATE TABLE academic_year (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(20) NOT NULL,
    creation_date DATE NOT NULL,
    archive_date DATE,
    status VARCHAR(255) NOT NULL,
    CONSTRAINT pk_academic_year PRIMARY KEY (id),
    CONSTRAINT uk_academic_year_name UNIQUE (name)
) ENGINE=InnoDB;

CREATE TABLE admin (
    id BIGINT NOT NULL,
    email VARCHAR(150) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(120) NOT NULL,
    last_name VARCHAR(120) NOT NULL,
    is_active BOOLEAN NOT NULL,
    credential_version BIGINT NOT NULL,
    failed_login_attempts INT NOT NULL DEFAULT 0,
    login_locked_until DATETIME(6),
    password_change_required BOOLEAN NOT NULL,
    role VARCHAR(32) NOT NULL DEFAULT 'ROLE_ADMIN',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6),
    CONSTRAINT pk_admin PRIMARY KEY (id),
    CONSTRAINT uk_admin_email UNIQUE (email)
) ENGINE=InnoDB;

CREATE TABLE member (
    member_id BIGINT NOT NULL,
    username VARCHAR(150) NOT NULL,
    email VARCHAR(150) NOT NULL,
    password_hash VARCHAR(255),
    credential_version BIGINT NOT NULL,
    failed_login_attempts INT NOT NULL,
    login_locked_until DATETIME(6),
    first_name VARCHAR(120),
    last_name VARCHAR(120),
    phone_number VARCHAR(25),
    date_of_birth DATETIME(6),
    address VARCHAR(300),
    account_status ENUM('UNVERIFIED', 'ACTIVE', 'SUSPENDED') NOT NULL DEFAULT 'UNVERIFIED',
    email_verified_at DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_member PRIMARY KEY (member_id),
    CONSTRAINT uk_member_username UNIQUE (username),
    CONSTRAINT uk_member_email UNIQUE (email)
) ENGINE=InnoDB;

CREATE TABLE one_time_code (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    purpose VARCHAR(32) NOT NULL,
    code_hash VARCHAR(255) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    attempt_count INT NOT NULL,
    is_used BOOLEAN NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_one_time_code PRIMARY KEY (id),
    CONSTRAINT fk_one_time_code_member FOREIGN KEY (member_id) REFERENCES member (member_id)
) ENGINE=InnoDB;

CREATE TABLE applicant (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_applicant PRIMARY KEY (id),
    CONSTRAINT fk_applicant_member FOREIGN KEY (member_id) REFERENCES member (member_id)
) ENGINE=InnoDB;

CREATE TABLE identity_document (
    identity_document_id BIGINT NOT NULL,
    applicant_id BIGINT,
    member_id BIGINT,
    document_type VARCHAR(32) NOT NULL,
    encrypted_document_number VARCHAR(8192) NOT NULL,
    document_number_hash VARCHAR(64) NOT NULL,
    issuing_country VARCHAR(2),
    primary_document BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_identity_document PRIMARY KEY (identity_document_id),
    CONSTRAINT uk_identity_document_applicant_type UNIQUE (applicant_id, document_type),
    CONSTRAINT uk_identity_document_number_hash UNIQUE (document_number_hash),
    CONSTRAINT fk_identity_document_applicant FOREIGN KEY (applicant_id) REFERENCES applicant (id),
    CONSTRAINT fk_identity_document_member FOREIGN KEY (member_id) REFERENCES member (member_id),
    CONSTRAINT ck_identity_document_owner CHECK (applicant_id IS NOT NULL OR member_id IS NOT NULL)
) ENGINE=InnoDB;

CREATE TABLE programme_level (
    id BIGINT NOT NULL AUTO_INCREMENT,
    level_code VARCHAR(10) NOT NULL,
    programme_name VARCHAR(120) NOT NULL,
    academic_year_id BIGINT NOT NULL,
    seat_quota INT NOT NULL,
    admission_open BOOLEAN NOT NULL,
    application_start_date DATE,
    application_end_date DATE,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_programme_level PRIMARY KEY (id),
    CONSTRAINT uk_programme_level_year_code UNIQUE (academic_year_id, level_code),
    CONSTRAINT fk_programme_level_academic_year FOREIGN KEY (academic_year_id) REFERENCES academic_year (id),
    CONSTRAINT ck_programme_level_application_dates CHECK (application_start_date < application_end_date)
) ENGINE=InnoDB;

CREATE TABLE application (
    application_id BIGINT NOT NULL,
    application_number VARCHAR(30) NOT NULL,
    member_id BIGINT NOT NULL,
    applicant_id BIGINT NOT NULL,
    submitted_applicant_name VARCHAR(120) NOT NULL,
    submitted_name_hash VARBINARY(32) NOT NULL,
    submitted_date_of_birth DATE NOT NULL,
    submitted_gender VARCHAR(20) NOT NULL,
    submitted_phone VARCHAR(25) NOT NULL,
    submitted_previous_school VARCHAR(150),
    submitted_document_type VARCHAR(32) NOT NULL,
    submitted_document_number VARCHAR(4096) NOT NULL,
    submitted_document_issuing_country VARCHAR(2),
    programme_level_id BIGINT NOT NULL,
    academic_year_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    manual_identity_review_required BOOLEAN NOT NULL,
    submitted_at DATETIME(6) NOT NULL,
    declarations_accepted_at DATETIME(6),
    result_released_at DATETIME(6),
    acceptance_deadline DATETIME(6),
    offer_reminder_queued_at DATETIME(6),
    responded_at DATETIME(6),
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_application PRIMARY KEY (application_id),
    CONSTRAINT uk_application_number UNIQUE (application_number),
    INDEX idx_application_identity_review
        (programme_level_id, academic_year_id, submitted_date_of_birth, submitted_document_type, submitted_name_hash),
    INDEX idx_application_duplicate_match (submitted_date_of_birth, submitted_name_hash),
    CONSTRAINT fk_application_member FOREIGN KEY (member_id) REFERENCES member (member_id),
    CONSTRAINT fk_application_applicant FOREIGN KEY (applicant_id) REFERENCES applicant (id),
    CONSTRAINT fk_application_programme_level FOREIGN KEY (programme_level_id) REFERENCES programme_level (id),
    CONSTRAINT fk_application_academic_year FOREIGN KEY (academic_year_id) REFERENCES academic_year (id)
) ENGINE=InnoDB;

CREATE TABLE identity_review (
    id BIGINT NOT NULL AUTO_INCREMENT,
    first_applicant_id BIGINT NOT NULL,
    second_applicant_id BIGINT NOT NULL,
    first_application_id BIGINT NOT NULL,
    second_application_id BIGINT NOT NULL,
    reviewed_by BIGINT NOT NULL,
    same_person BOOLEAN NOT NULL,
    current_marker INT,
    reviewed_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_identity_review PRIMARY KEY (id),
    CONSTRAINT uk_identity_review_current UNIQUE (first_applicant_id, second_applicant_id, current_marker),
    CONSTRAINT fk_identity_review_first_applicant FOREIGN KEY (first_applicant_id) REFERENCES applicant (id),
    CONSTRAINT fk_identity_review_second_applicant FOREIGN KEY (second_applicant_id) REFERENCES applicant (id),
    CONSTRAINT fk_identity_review_first_application FOREIGN KEY (first_application_id) REFERENCES application (application_id),
    CONSTRAINT fk_identity_review_second_application FOREIGN KEY (second_application_id) REFERENCES application (application_id),
    CONSTRAINT fk_identity_review_admin FOREIGN KEY (reviewed_by) REFERENCES admin (id)
) ENGINE=InnoDB;

CREATE TABLE identity_review_lock (
    lock_id BIGINT NOT NULL,
    CONSTRAINT pk_identity_review_lock PRIMARY KEY (lock_id)
) ENGINE=InnoDB;

CREATE TABLE status_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    application_id BIGINT NOT NULL,
    from_status VARCHAR(20),
    to_status VARCHAR(20) NOT NULL,
    changed_by_role VARCHAR(20) NOT NULL,
    changed_by_id BIGINT,
    remarks VARCHAR(255),
    changed_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_status_history PRIMARY KEY (id),
    CONSTRAINT fk_status_history_application FOREIGN KEY (application_id) REFERENCES application (application_id)
) ENGINE=InnoDB;

CREATE TABLE email_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    application_id BIGINT,
    template_type VARCHAR(40) NOT NULL,
    recipient_email VARCHAR(150) NOT NULL,
    applicant_name VARCHAR(120),
    application_number VARCHAR(30),
    offer_deadline_snapshot DATETIME(6),
    action_required VARCHAR(255) NOT NULL,
    subject VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    retry_count INT NOT NULL,
    error_message VARCHAR(500),
    sent_at DATETIME(6),
    skipped_by_admin_id BIGINT,
    skipped_at DATETIME(6),
    skip_reason VARCHAR(255),
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_email_log PRIMARY KEY (id),
    CONSTRAINT fk_email_log_member FOREIGN KEY (member_id) REFERENCES member (member_id),
    CONSTRAINT fk_email_log_application FOREIGN KEY (application_id) REFERENCES application (application_id)
) ENGINE=InnoDB;

CREATE TABLE registration_rate_limit (
    rate_key VARCHAR(64) NOT NULL,
    window_started_at DATETIME(6) NOT NULL,
    request_count INT NOT NULL,
    CONSTRAINT pk_registration_rate_limit PRIMARY KEY (rate_key),
    INDEX idx_registration_rate_limit_window (window_started_at)
) ENGINE=InnoDB;
