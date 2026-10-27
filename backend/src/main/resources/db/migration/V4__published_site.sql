-- The public site serves from these snapshots; admin edits are a draft until "Publish".
CREATE TABLE published_site (
    kind         VARCHAR(20) PRIMARY KEY,
    payload      TEXT NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE NOT NULL
);
