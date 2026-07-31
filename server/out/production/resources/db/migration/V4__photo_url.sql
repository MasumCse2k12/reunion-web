-- Profile photo stored in MinIO; the column holds the full public URL.
-- NULL = no photo uploaded yet.
alter table person add column if not exists photo_url text;
