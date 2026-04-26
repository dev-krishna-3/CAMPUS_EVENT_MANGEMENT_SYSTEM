USE campus_event_db;

-- 1. Core Updates (if needed)
ALTER TABLE events ADD COLUMN IF NOT EXISTS created_by INT;
ALTER TABLE events ADD COLUMN IF NOT EXISTS category_id INT;
ALTER TABLE events ADD COLUMN IF NOT EXISTS event_date TIMESTAMP;

-- 2. Audit Logs
CREATE TABLE IF NOT EXISTS audit_logs (
    id INT PRIMARY KEY AUTO_INCREMENT, 
    actor_email VARCHAR(150) NOT NULL, 
    action_name VARCHAR(80) NOT NULL, 
    details VARCHAR(255) NOT NULL, 
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 3. QR Codes
CREATE TABLE IF NOT EXISTS qr_codes (
    id INT PRIMARY KEY AUTO_INCREMENT, 
    user_id INT, 
    event_id INT, 
    qr_data VARCHAR(255) UNIQUE, 
    is_used BOOLEAN DEFAULT FALSE, 
    scanned_at TIMESTAMP NULL, 
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, 
    CONSTRAINT fk_qr_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE, 
    CONSTRAINT fk_qr_event FOREIGN KEY(event_id) REFERENCES events(id) ON DELETE CASCADE
);

-- 4. Feedback
CREATE TABLE IF NOT EXISTS feedback (
    id INT PRIMARY KEY AUTO_INCREMENT, 
    user_id INT, 
    event_id INT, 
    rating INT, 
    comment VARCHAR(500), 
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, 
    UNIQUE KEY uq_feedback(user_id, event_id), 
    CONSTRAINT fk_fb_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE, 
    CONSTRAINT fk_fb_event FOREIGN KEY(event_id) REFERENCES events(id) ON DELETE CASCADE
);

-- 5. Clubs
CREATE TABLE IF NOT EXISTS clubs (
    id INT PRIMARY KEY AUTO_INCREMENT, 
    name VARCHAR(100) NOT NULL UNIQUE, 
    description VARCHAR(255), 
    created_by INT, 
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS club_members (
    id INT PRIMARY KEY AUTO_INCREMENT, 
    club_id INT, 
    user_id INT, 
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, 
    UNIQUE KEY uq_club_user(club_id, user_id), 
    CONSTRAINT fk_cm_club FOREIGN KEY(club_id) REFERENCES clubs(id) ON DELETE CASCADE, 
    CONSTRAINT fk_cm_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 6. Volunteer Policy
CREATE TABLE IF NOT EXISTS event_volunteer_policy (
    event_id INT PRIMARY KEY, 
    policy_type VARCHAR(50), 
    application_mode VARCHAR(50), 
    max_volunteers INT, 
    club_id INT, 
    CONSTRAINT fk_evp_event FOREIGN KEY(event_id) REFERENCES events(id) ON DELETE CASCADE, 
    CONSTRAINT fk_evp_club FOREIGN KEY(club_id) REFERENCES clubs(id) ON DELETE SET NULL
);

-- 7. Volunteer Teams
CREATE TABLE IF NOT EXISTS volunteer_teams (
    id INT PRIMARY KEY AUTO_INCREMENT, 
    event_id INT, 
    team_name VARCHAR(100), 
    max_members INT, 
    UNIQUE KEY uq_team_event(event_id, team_name), 
    CONSTRAINT fk_vt_event FOREIGN KEY(event_id) REFERENCES events(id) ON DELETE CASCADE
);

-- 8. Volunteer Applications
CREATE TABLE IF NOT EXISTS volunteer_applications (
    id INT PRIMARY KEY AUTO_INCREMENT, 
    event_id INT, 
    user_id INT, 
    team_id INT, 
    application_type VARCHAR(50), 
    team_leader_id INT, 
    status VARCHAR(50) DEFAULT 'PENDING', 
    note VARCHAR(255), 
    applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, 
    reviewed_at TIMESTAMP NULL, 
    UNIQUE KEY uq_va_user_event(user_id, event_id), 
    CONSTRAINT fk_va_event FOREIGN KEY(event_id) REFERENCES events(id) ON DELETE CASCADE, 
    CONSTRAINT fk_va_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE, 
    CONSTRAINT fk_va_team FOREIGN KEY(team_id) REFERENCES volunteer_teams(id) ON DELETE SET NULL
);

-- 9. Volunteer Tasks
CREATE TABLE IF NOT EXISTS volunteer_tasks (
    id INT PRIMARY KEY AUTO_INCREMENT, 
    event_id INT, 
    team_id INT, 
    assigned_to INT, 
    title VARCHAR(150), 
    description VARCHAR(500), 
    status VARCHAR(30) DEFAULT 'TODO', 
    priority VARCHAR(30) DEFAULT 'MEDIUM', 
    due_date TIMESTAMP NULL, 
    created_by INT, 
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, 
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, 
    CONSTRAINT fk_vtask_event FOREIGN KEY(event_id) REFERENCES events(id) ON DELETE CASCADE, 
    CONSTRAINT fk_vtask_team FOREIGN KEY(team_id) REFERENCES volunteer_teams(id) ON DELETE SET NULL, 
    CONSTRAINT fk_vtask_user FOREIGN KEY(assigned_to) REFERENCES users(id) ON DELETE SET NULL
);

-- 10. Volunteer Activity Logs
CREATE TABLE IF NOT EXISTS volunteer_activity_logs (
    id INT PRIMARY KEY AUTO_INCREMENT, 
    task_id INT, 
    user_id INT, 
    log_text VARCHAR(500), 
    hours_spent DOUBLE, 
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, 
    CONSTRAINT fk_valog_task FOREIGN KEY(task_id) REFERENCES volunteer_tasks(id) ON DELETE CASCADE, 
    CONSTRAINT fk_valog_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 11. Volunteer Attendance
CREATE TABLE IF NOT EXISTS volunteer_attendance (
    id INT PRIMARY KEY AUTO_INCREMENT, 
    event_id INT, 
    user_id INT, 
    check_in_time TIMESTAMP NULL, 
    check_out_time TIMESTAMP NULL, 
    check_in_method VARCHAR(50), 
    attendance_date DATE, 
    CONSTRAINT fk_vatt_event FOREIGN KEY(event_id) REFERENCES events(id) ON DELETE CASCADE, 
    CONSTRAINT fk_vatt_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);
