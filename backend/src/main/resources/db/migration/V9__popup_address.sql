-- A street address for pop-ups, alongside the venue name; shown publicly with a map link.
ALTER TABLE popup_event ADD COLUMN address VARCHAR(200);
