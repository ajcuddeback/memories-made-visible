-- Uploaded photos are now stored in several widths for responsive <img srcset>. The comma-separated list of
-- available widths (ascending, the last one being the full-size object at s3_key) lives here; NULL means the
-- photo predates renditions and only the full-size object exists.
ALTER TABLE site_photo ADD COLUMN widths VARCHAR(60);
