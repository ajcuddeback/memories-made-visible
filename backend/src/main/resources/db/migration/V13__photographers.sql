-- Customers can pick a photographer. A staff account flagged bookable appears on the booking page;
-- time blocks can then belong to one person (NULL = the whole studio).
ALTER TABLE admin_user ADD COLUMN bookable BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE time_block ADD COLUMN staff_name VARCHAR(60) REFERENCES admin_user (name) ON DELETE CASCADE;
