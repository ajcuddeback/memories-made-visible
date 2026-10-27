-- Categories become admin-managed: a short label for filters/captions, and
-- packages get a "what's included" list (one item per line).
ALTER TABLE session_type ADD COLUMN short_name VARCHAR(40);
ALTER TABLE pricing_tier ADD COLUMN includes_text TEXT NOT NULL DEFAULT '';
