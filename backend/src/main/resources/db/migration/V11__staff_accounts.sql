-- Staff accounts: an access level (OWNER / ADMIN / EMPLOYEE) separate from the job title,
-- an email for invites and resets, and a first-sign-in password change.
ALTER TABLE admin_user ADD COLUMN email VARCHAR(200);
ALTER TABLE admin_user ADD COLUMN access VARCHAR(10) NOT NULL DEFAULT 'ADMIN';
ALTER TABLE admin_user ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE admin_user ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE admin_user ADD COLUMN reset_token VARCHAR(80);
ALTER TABLE admin_user ADD COLUMN reset_expires TIMESTAMP WITH TIME ZONE;
ALTER TABLE admin_user ADD COLUMN created_at TIMESTAMP WITH TIME ZONE;
-- Case-insensitive uniqueness is enforced by the API; this index just makes lookups quick.
CREATE INDEX idx_admin_user_email ON admin_user (email);
