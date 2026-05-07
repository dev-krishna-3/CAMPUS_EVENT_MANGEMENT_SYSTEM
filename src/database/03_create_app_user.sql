

CREATE DATABASE IF NOT EXISTS campus_event_db;

CREATE USER IF NOT EXISTS 'event_app'@'localhost' IDENTIFIED BY 'event_app_123';
GRANT ALL PRIVILEGES ON campus_event_db.* TO 'event_app'@'localhost';
FLUSH PRIVILEGES;
