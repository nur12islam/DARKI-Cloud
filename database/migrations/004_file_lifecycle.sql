-- DARKI Cloud — file lifecycle metadata
-- Adds deletion timestamps to storage objects so remote cleanup can be retried safely.

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'storage_objects_deleted_state_consistent'
    ) THEN
        ALTER TABLE storage_objects
            ADD CONSTRAINT storage_objects_deleted_state_consistent
            CHECK ((state = 'deleted') = (deleted_at IS NOT NULL));
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS storage_objects_deleting_idx
    ON storage_objects (modified_at)
    WHERE state = 'deleting';
