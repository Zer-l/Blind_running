-- ============================================
-- 助盲跑 课设版数据库 Schema
-- MySQL 8.0.16+
-- ============================================

-- ============================================
-- 1. users 用户表
-- ============================================
CREATE TABLE users (
    id                  VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    phone               VARCHAR(20) NOT NULL,
    nickname            VARCHAR(50) NOT NULL,
    avatar_url          VARCHAR(500),
    gender              ENUM('MALE', 'FEMALE', 'OTHER'),
    birthday            DATE,

    roles               JSON NOT NULL,
    blind_profile       JSON,
    volunteer_profile   JSON,

    total_runs          INT NOT NULL DEFAULT 0,
    total_hours_minutes INT NOT NULL DEFAULT 0,
    rating_sum          INT NOT NULL DEFAULT 0,
    rating_count        INT NOT NULL DEFAULT 0,

    status              ENUM('ACTIVE', 'FROZEN', 'BANNED') NOT NULL DEFAULT 'ACTIVE',

    created_at          TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                        ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at          TIMESTAMP(3) NULL,

    CONSTRAINT uk_users_phone UNIQUE (phone),
    INDEX idx_users_created_at (created_at DESC),
    INDEX idx_users_is_volunteer ((JSON_CONTAINS(roles, '"VOLUNTEER"'))),
    INDEX idx_users_is_blind ((JSON_CONTAINS(roles, '"BLIND_RUNNER"')))
) ENGINE=InnoDB;


-- ============================================
-- 2. refresh_tokens 刷新令牌表
-- ============================================
CREATE TABLE refresh_tokens (
    id           VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    user_id      VARCHAR(36) NOT NULL,
    token_hash   VARCHAR(64) NOT NULL,
    expires_at   TIMESTAMP(3) NOT NULL,
    revoked      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    last_used_at TIMESTAMP(3) NULL,
    user_agent   VARCHAR(200),

    CONSTRAINT fk_rt_user FOREIGN KEY (user_id) REFERENCES users(id),
    UNIQUE KEY uk_token_hash (token_hash),
    INDEX idx_rt_user (user_id, revoked, expires_at)
) ENGINE=InnoDB;


-- ============================================
-- 3. run_requests 跑步请求表（核心）
-- ============================================
CREATE TABLE run_requests (
    id                          VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    blind_runner_id             VARCHAR(36) NOT NULL,
    volunteer_id                VARCHAR(36) NULL,

    status                      ENUM(
                                  'CREATED', 'MATCHING', 'ACCEPTED',
                                  'EN_ROUTE', 'MET', 'RUNNING',
                                  'FINISHED', 'CLOSED', 'ABORTED'
                                ) NOT NULL DEFAULT 'CREATED',

    created_at                  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    matched_at                  TIMESTAMP(3) NULL,
    departed_at                 TIMESTAMP(3) NULL,
    met_at                      TIMESTAMP(3) NULL,
    run_started_at              TIMESTAMP(3) NULL,
    run_ended_at                TIMESTAMP(3) NULL,
    closed_at                   TIMESTAMP(3) NULL,

    expected_duration_minutes   INT NOT NULL,
    expected_distance_meters    INT,
    expected_pace_seconds       INT,

    meeting_lat                 DOUBLE NOT NULL,
    meeting_lng                 DOUBLE NOT NULL,
    meeting_location_desc       VARCHAR(200) NOT NULL,

    actual_distance_meters      INT,
    actual_duration_seconds     INT,

    notes                       TEXT,
    abort_reason                VARCHAR(200),
    abort_by                    ENUM('BLIND', 'VOLUNTEER', 'SYSTEM', 'ADMIN') NULL,

    version                     INT NOT NULL DEFAULT 0,
    updated_at                  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                ON UPDATE CURRENT_TIMESTAMP(3),

    CONSTRAINT fk_req_blind FOREIGN KEY (blind_runner_id) REFERENCES users(id),
    CONSTRAINT fk_req_vol   FOREIGN KEY (volunteer_id)    REFERENCES users(id),

    INDEX idx_req_blind       (blind_runner_id, created_at DESC),
    INDEX idx_req_volunteer   (volunteer_id, created_at DESC),
    INDEX idx_req_status      (status, created_at DESC),
    INDEX idx_req_matching_loc(status, meeting_lat, meeting_lng)
) ENGINE=InnoDB;


-- ============================================
-- 4. run_request_events 状态变更日志
-- ============================================
CREATE TABLE run_request_events (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id     VARCHAR(36) NOT NULL,
    from_status    VARCHAR(20) NULL,
    to_status      VARCHAR(20) NOT NULL,
    triggered_by   VARCHAR(36) NULL,
    triggered_role ENUM('BLIND', 'VOLUNTEER', 'SYSTEM', 'ADMIN') NOT NULL,
    reason         VARCHAR(500),
    metadata       JSON,
    occurred_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    CONSTRAINT fk_event_req  FOREIGN KEY (request_id)   REFERENCES run_requests(id),
    CONSTRAINT fk_event_user FOREIGN KEY (triggered_by) REFERENCES users(id),

    INDEX idx_events_request (request_id, occurred_at)
) ENGINE=InnoDB;


-- ============================================
-- 5. run_tracks 跑步轨迹表
-- ============================================
CREATE TABLE run_tracks (
    id                     VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    request_id             VARCHAR(36) NOT NULL,
    user_id                VARCHAR(36) NOT NULL,
    role                   ENUM('BLIND', 'VOLUNTEER') NOT NULL,

    points                 JSON NOT NULL,
    point_count            INT NOT NULL,

    total_distance_meters  INT NOT NULL,
    total_duration_seconds INT NOT NULL,
    avg_pace_seconds       INT,
    max_speed              FLOAT,

    created_at             TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    CONSTRAINT fk_track_req  FOREIGN KEY (request_id) REFERENCES run_requests(id),
    CONSTRAINT fk_track_user FOREIGN KEY (user_id)    REFERENCES users(id),
    CONSTRAINT uk_track_req_user UNIQUE (request_id, user_id),

    INDEX idx_tracks_user (user_id, created_at DESC)
) ENGINE=InnoDB;


-- ============================================
-- 6. reviews 评价表
-- ============================================
CREATE TABLE reviews (
    id          VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),
    request_id  VARCHAR(36) NOT NULL,
    reviewer_id VARCHAR(36) NOT NULL,
    reviewee_id VARCHAR(36) NOT NULL,

    rating      INT NOT NULL,
    CONSTRAINT chk_rating CHECK (rating BETWEEN 1 AND 5),

    tags        JSON NOT NULL,
    comment     TEXT,
    voice_url   VARCHAR(500),

    created_at  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    CONSTRAINT fk_review_req FOREIGN KEY (request_id)  REFERENCES run_requests(id),
    CONSTRAINT fk_review_er  FOREIGN KEY (reviewer_id) REFERENCES users(id),
    CONSTRAINT fk_review_ee  FOREIGN KEY (reviewee_id) REFERENCES users(id),
    CONSTRAINT uk_review_req_reviewer UNIQUE (request_id, reviewer_id),

    INDEX idx_reviews_reviewee (reviewee_id, created_at DESC)
) ENGINE=InnoDB;
