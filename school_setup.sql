-- school_setup.sql
-- Create the tables and insert some mock data for testing

CREATE TABLE IF NOT EXISTS students (
    id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    pin VARCHAR(10)
);

CREATE TABLE IF NOT EXISTS grades (
    id SERIAL PRIMARY KEY,
    student_id VARCHAR(50) REFERENCES students(id),
    course_name VARCHAR(100) NOT NULL,
    grade VARCHAR(5) NOT NULL
);

CREATE TABLE IF NOT EXISTS summer_camp (
    id SERIAL PRIMARY KEY,
    student_id VARCHAR(50) REFERENCES students(id),
    sport_name VARCHAR(100) NOT NULL,
    enrollment_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Clear existing data if re-running
TRUNCATE TABLE summer_camp CASCADE;
TRUNCATE TABLE grades CASCADE;
TRUNCATE TABLE students CASCADE;

-- Insert a mock student (ID: 1234)
INSERT INTO students (id, name, pin) VALUES ('1234', 'John Doe', '0000');

-- Insert mock grades for student 1234
INSERT INTO grades (student_id, course_name, grade) VALUES ('1234', 'Mathematics', 'A');
INSERT INTO grades (student_id, course_name, grade) VALUES ('1234', 'Science', 'B');
INSERT INTO grades (student_id, course_name, grade) VALUES ('1234', 'History', 'A');
