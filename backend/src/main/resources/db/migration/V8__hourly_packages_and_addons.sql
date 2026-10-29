-- Packages can be priced by the hour (with a minimum) and carry optional add-ons.
ALTER TABLE pricing_tier ADD COLUMN pricing_model VARCHAR(10) NOT NULL DEFAULT 'FLAT';
ALTER TABLE pricing_tier ADD COLUMN min_hours INTEGER NOT NULL DEFAULT 2;
ALTER TABLE pricing_tier ADD COLUMN max_hours INTEGER NOT NULL DEFAULT 8;
-- One add-on per line: name|price|flat-or-hour
ALTER TABLE pricing_tier ADD COLUMN addons_text TEXT NOT NULL DEFAULT '';
ALTER TABLE booking ADD COLUMN hours INTEGER;
ALTER TABLE booking ADD COLUMN addons_text VARCHAR(300);

-- The launch "Events" category: hourly coverage with the second photographer as an add-on, not a package.
UPDATE pricing_tier SET pricing_model = 'HOURLY', min_hours = 2, max_hours = 8, addons_text = 'Second photographer|75|hour'
 WHERE session_type_key = 'events' AND name = 'Event coverage';
DELETE FROM pricing_tier WHERE session_type_key = 'events' AND name = 'Second photographer';
