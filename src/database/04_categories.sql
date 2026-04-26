USE campus_event_db;

-- 1. Create categories table
CREATE TABLE IF NOT EXISTS categories (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(255)
);

-- 2. Seed initial categories
INSERT IGNORE INTO categories (name, description) VALUES 
('Technical', 'Coding, Robotics, and Tech workshops'),
('Cultural', 'Music, Dance, and Art festivals'),
('Sports', 'Cricket, Football, and Indoor games'),
('Workshop', 'Educational and Skill development sessions'),
('General', 'Miscellaneous events');

-- 3. Update events table
ALTER TABLE events 
ADD COLUMN category_id INT,
ADD COLUMN event_date TIMESTAMP;

-- 4. Set existing events to 'General' category (assuming General has ID 5 if it's the 5th insert, but safer to query)
UPDATE events SET category_id = (SELECT id FROM categories WHERE name = 'General' LIMIT 1), event_date = created_at WHERE category_id IS NULL;

-- 5. Add Foreign Key constraint
ALTER TABLE events
ADD CONSTRAINT fk_event_category FOREIGN KEY (category_id) REFERENCES categories(id);
