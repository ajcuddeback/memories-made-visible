-- Delivering a gallery records the client's photo link and when it went out.
ALTER TABLE gallery ADD COLUMN link VARCHAR(500);
ALTER TABLE gallery ADD COLUMN delivered_at TIMESTAMP WITH TIME ZONE;
