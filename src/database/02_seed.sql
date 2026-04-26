USE campus_event_db;

INSERT IGNORE INTO roles(role_name) VALUES
('student'),
('admin'),
('super_admin');

INSERT INTO users(name, email, password, role_id)
VALUES
('Krishna', 'k@gmail.com', '123', (SELECT id FROM roles WHERE role_name = 'student')),
('Admin', 'admin@gmail.com', 'admin', (SELECT id FROM roles WHERE role_name = 'admin')),
('Super Admin', 'superadmin@gmail.com', 'super123', (SELECT id FROM roles WHERE role_name = 'super_admin'))
ON DUPLICATE KEY UPDATE id = id;
