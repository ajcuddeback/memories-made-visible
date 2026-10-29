-- Session length lives on the package the customer picks, not just the category.
ALTER TABLE pricing_tier ADD COLUMN duration_minutes INTEGER NOT NULL DEFAULT 60;
ALTER TABLE booking ADD COLUMN package_name VARCHAR(80);
