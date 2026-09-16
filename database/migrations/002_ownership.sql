-- DARKI Cloud — ownership hardening
-- Safe for both the original Milestone 1 schema and the hardened base schema.

ALTER TABLE storage_objects
    ADD COLUMN IF NOT EXISTS user_id UUID;

UPDATE storage_objects so
SET user_id = f.user_id
FROM files f
WHERE f.storage_object_id = so.id
  AND so.user_id IS NULL;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM storage_objects WHERE user_id IS NULL) THEN
        RAISE EXCEPTION 'Cannot harden storage ownership: orphaned storage_objects exist';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM folders child
        JOIN folders parent ON parent.id = child.parent_id
        WHERE child.parent_id IS NOT NULL
          AND child.user_id <> parent.user_id
    ) THEN
        RAISE EXCEPTION 'Cannot harden folder ownership: cross-user parent reference exists';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM files f
        JOIN folders folder ON folder.id = f.folder_id
        WHERE f.user_id <> folder.user_id
    ) THEN
        RAISE EXCEPTION 'Cannot harden file ownership: cross-user folder reference exists';
    END IF;
END $$;

ALTER TABLE storage_objects
    ALTER COLUMN user_id SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'folders_user_id_id_unique'
    ) THEN
        ALTER TABLE folders ADD CONSTRAINT folders_user_id_id_unique UNIQUE (user_id, id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'storage_objects_user_id_id_unique'
    ) THEN
        ALTER TABLE storage_objects ADD CONSTRAINT storage_objects_user_id_id_unique UNIQUE (user_id, id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'folders_parent_same_user_fk'
    ) THEN
        ALTER TABLE folders ADD CONSTRAINT folders_parent_same_user_fk
            FOREIGN KEY (user_id, parent_id) REFERENCES folders (user_id, id) ON DELETE RESTRICT;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'files_folder_same_user_fk'
    ) THEN
        ALTER TABLE files ADD CONSTRAINT files_folder_same_user_fk
            FOREIGN KEY (user_id, folder_id) REFERENCES folders (user_id, id) ON DELETE RESTRICT;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'files_storage_object_same_user_fk'
    ) THEN
        ALTER TABLE files ADD CONSTRAINT files_storage_object_same_user_fk
            FOREIGN KEY (user_id, storage_object_id) REFERENCES storage_objects (user_id, id) ON DELETE RESTRICT;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'sync_changes_device_same_user_fk'
    ) THEN
        ALTER TABLE sync_changes ADD CONSTRAINT sync_changes_device_same_user_fk
            FOREIGN KEY (user_id, device_id) REFERENCES devices (user_id, id) ON DELETE SET NULL;
    END IF;
END $$;
