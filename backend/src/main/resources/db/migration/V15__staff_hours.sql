-- A person's own booking week. When present it replaces the studio's hours for that person; the studio's
-- hours (availability_hours) remain the default for everyone else.
CREATE TABLE staff_hours (
    staff_name VARCHAR(60) NOT NULL REFERENCES admin_user (name) ON DELETE CASCADE,
    weekday    INTEGER     NOT NULL,
    open       BOOLEAN     NOT NULL DEFAULT TRUE,
    start_time TIME        NOT NULL DEFAULT '07:00',
    end_time   TIME        NOT NULL DEFAULT '18:00',
    PRIMARY KEY (staff_name, weekday)
);
