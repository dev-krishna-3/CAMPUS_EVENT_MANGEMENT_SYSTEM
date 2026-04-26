/*
 * Decompiled with CFR 0.152.
 */
package db;

import db.DBConnection;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseInitializer {
    private DatabaseInitializer() {
    }

    public static boolean initialize() {
        try (Connection connection = DBConnection.getConnection();
             Statement statement = connection.createStatement();){
            // Core Tables
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS roles (id TINYINT PRIMARY KEY AUTO_INCREMENT, role_name VARCHAR(40) NOT NULL UNIQUE)");
            
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS users (id INT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(100) NOT NULL, email VARCHAR(150) NOT NULL UNIQUE, password VARCHAR(255) NOT NULL, role_id TINYINT NOT NULL, is_active BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, CONSTRAINT fk_users_role FOREIGN KEY(role_id) REFERENCES roles(id))");
            
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS categories (id INT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(100) NOT NULL UNIQUE, description VARCHAR(255))");

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS events (id INT PRIMARY KEY AUTO_INCREMENT, title VARCHAR(150) NOT NULL, venue VARCHAR(150) NOT NULL, capacity INT NOT NULL, available_seats INT NOT NULL, status VARCHAR(30) NOT NULL DEFAULT 'open', created_by INT, category_id INT, event_date TIMESTAMP NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, CONSTRAINT chk_capacity CHECK (capacity > 0), CONSTRAINT chk_available CHECK (available_seats >= 0), CONSTRAINT fk_event_category FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE SET NULL, CONSTRAINT fk_event_creator FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL)");
            
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS registrations (id INT PRIMARY KEY AUTO_INCREMENT, user_id INT NOT NULL, event_id INT NOT NULL, registration_status VARCHAR(30) NOT NULL DEFAULT 'registered', registered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE KEY uq_user_event (user_id, event_id), CONSTRAINT fk_reg_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE, CONSTRAINT fk_reg_event FOREIGN KEY(event_id) REFERENCES events(id) ON DELETE CASCADE)");

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS audit_logs (id INT PRIMARY KEY AUTO_INCREMENT, actor_email VARCHAR(150) NOT NULL, action_name VARCHAR(80) NOT NULL, details VARCHAR(255) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS qr_codes (id INT PRIMARY KEY AUTO_INCREMENT, user_id INT, event_id INT, qr_data VARCHAR(255) UNIQUE, is_used BOOLEAN DEFAULT FALSE, scanned_at TIMESTAMP NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, CONSTRAINT fk_qr_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE, CONSTRAINT fk_qr_event FOREIGN KEY(event_id) REFERENCES events(id) ON DELETE CASCADE)");

            // Feedback & Clubs
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS feedback (id INT PRIMARY KEY AUTO_INCREMENT, user_id INT, event_id INT, rating INT, comment VARCHAR(500), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, UNIQUE KEY uq_feedback(user_id, event_id), CONSTRAINT fk_fb_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE, CONSTRAINT fk_fb_event FOREIGN KEY(event_id) REFERENCES events(id) ON DELETE CASCADE)");
            
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS clubs (id INT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(100) NOT NULL UNIQUE, description VARCHAR(255), created_by INT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS club_members (id INT PRIMARY KEY AUTO_INCREMENT, club_id INT, user_id INT, joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, UNIQUE KEY uq_club_user(club_id, user_id), CONSTRAINT fk_cm_club FOREIGN KEY(club_id) REFERENCES clubs(id) ON DELETE CASCADE, CONSTRAINT fk_cm_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE)");

            // Volunteer Management
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS event_volunteer_policy (event_id INT PRIMARY KEY, policy_type VARCHAR(50), application_mode VARCHAR(50), max_volunteers INT, club_id INT, CONSTRAINT fk_evp_event FOREIGN KEY(event_id) REFERENCES events(id) ON DELETE CASCADE, CONSTRAINT fk_evp_club FOREIGN KEY(club_id) REFERENCES clubs(id) ON DELETE SET NULL)");
            
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS volunteer_teams (id INT PRIMARY KEY AUTO_INCREMENT, event_id INT, team_name VARCHAR(100), max_members INT, UNIQUE KEY uq_team_event(event_id, team_name), CONSTRAINT fk_vt_event FOREIGN KEY(event_id) REFERENCES events(id) ON DELETE CASCADE)");
            
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS volunteer_applications (id INT PRIMARY KEY AUTO_INCREMENT, event_id INT, user_id INT, team_id INT, application_type VARCHAR(50), team_leader_id INT, status VARCHAR(50) DEFAULT 'PENDING', note VARCHAR(255), applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, reviewed_at TIMESTAMP NULL, UNIQUE KEY uq_va_user_event(user_id, event_id), CONSTRAINT fk_va_event FOREIGN KEY(event_id) REFERENCES events(id) ON DELETE CASCADE, CONSTRAINT fk_va_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE, CONSTRAINT fk_va_team FOREIGN KEY(team_id) REFERENCES volunteer_teams(id) ON DELETE SET NULL)");
            
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS volunteer_tasks (id INT PRIMARY KEY AUTO_INCREMENT, event_id INT, team_id INT, assigned_to INT, title VARCHAR(150), description VARCHAR(500), status VARCHAR(30) DEFAULT 'TODO', priority VARCHAR(30) DEFAULT 'MEDIUM', due_date TIMESTAMP NULL, created_by INT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, CONSTRAINT fk_vtask_event FOREIGN KEY(event_id) REFERENCES events(id) ON DELETE CASCADE, CONSTRAINT fk_vtask_team FOREIGN KEY(team_id) REFERENCES volunteer_teams(id) ON DELETE SET NULL, CONSTRAINT fk_vtask_user FOREIGN KEY(assigned_to) REFERENCES users(id) ON DELETE SET NULL)");
            
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS volunteer_activity_logs (id INT PRIMARY KEY AUTO_INCREMENT, task_id INT, user_id INT, log_text VARCHAR(500), hours_spent DOUBLE, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, CONSTRAINT fk_valog_task FOREIGN KEY(task_id) REFERENCES volunteer_tasks(id) ON DELETE CASCADE, CONSTRAINT fk_valog_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE)");
            
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS volunteer_attendance (id INT PRIMARY KEY AUTO_INCREMENT, event_id INT, user_id INT, check_in_time TIMESTAMP NULL, check_out_time TIMESTAMP NULL, check_in_method VARCHAR(50), attendance_date DATE, CONSTRAINT fk_vatt_event FOREIGN KEY(event_id) REFERENCES events(id) ON DELETE CASCADE, CONSTRAINT fk_vatt_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE)");

            // Initial Data
            statement.executeUpdate("INSERT IGNORE INTO roles(role_name) VALUES ('student'), ('admin'), ('super_admin')");
            statement.executeUpdate("INSERT IGNORE INTO categories (name, description) VALUES ('Technical', 'Coding, Robotics, and Tech workshops'), ('Cultural', 'Music, Dance, and Art festivals'), ('Sports', 'Cricket, Football, and Indoor games'), ('Workshop', 'Educational and Skill development sessions'), ('General', 'Miscellaneous events')");

            System.out.println("[INFO] Database connected and initialized. Running in MySQL mode.");
            return true;
        }
        catch (SQLException sQLException) {
            System.out.println("[WARN] Database initialization failed: " + sQLException.getMessage());
            System.out.println("[WARN] Switching to offline memory mode. Update database/db.properties to re-enable MySQL.");
            return false;
        }
    }
}
