-- DARKI Cloud — file lifecycle metadata
-- Adds deletion timestamps to storage objects so remote cleanup can be retried safely.

ALTER TABLE storage_objects
    ADD CONSTRAINT storage_objects_deleted_state_consistent
    CHECK ((state = 'deleted') = (deleted_at IS NOT NULL));

CREATE INDEX IF NOT EXISTS storage_objects_deleting_idx
    ON storage_objects (modified_at)
    WHERE state = 'deleting';
