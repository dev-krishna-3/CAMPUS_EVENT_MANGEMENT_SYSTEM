-- Run this manually in MySQL as an admin user (e.g. root) once.
-- Then update src/database/db.properties with EVENT_DB_USER=event_app and EVENT_DB_PASSWORD=event_app_123

CREATE DATABASE IF NOT EXISTS campus_event_db;

CREATE USER IF NOT EXISTS 'event_app'@'localhost' IDENTIFIED BY 'event_app_123';
GRANT ALL PRIVILEGES ON campus_event_db.* TO 'event_app'@'localhost';
FLUSH PRIVILEGES;
