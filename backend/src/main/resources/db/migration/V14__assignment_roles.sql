-- A session has a lead photographer and, when the package calls for one, a second.
ALTER TABLE booking_assignment ADD COLUMN role VARCHAR(10) NOT NULL DEFAULT 'LEAD';
-- Whether a booking needs two photographers (from an add-on flagged as "adds a photographer").
ALTER TABLE booking ADD COLUMN needs_second BOOLEAN NOT NULL DEFAULT FALSE;

-- The launch second-photographer add-on brings an extra person.
UPDATE pricing_tier SET addons_text = 'Second photographer|75|hour|staff' WHERE addons_text = 'Second photographer|75|hour';
