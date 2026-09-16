-- DARKI Cloud — PostgreSQL schema
-- Authoritative virtual filesystem + sync state + database-enforced ownership.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    telegram_user_id BIGINT NOT NULL UNIQUE,
    display_name TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE devices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_name TEXT NOT NULL,
    platform TEXT NOT NULL,
    last_seen_at TIMESTAMPTZ,
    sync_cursor BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, id)
);

CREATE TABLE folders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    parent_id UUID,
    name TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    modified_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT folders_name_not_empty CHECK (length(trim(name)) > 0),
    CONSTRAINT folders_name_not_path CHECK (name NOT LIKE '%/%' AND name NOT LIKE '%\\%'),
    CONSTRAINT folders_name_not_dot CHECK (name NOT IN ('.', '..')),
    CONSTRAINT folders_name_length CHECK (length(name) <= 255),
    UNIQUE (user_id, id),
    CONSTRAINT folders_parent_same_user_fk
        FOREIGN KEY (user_id, parent_id) REFERENCES folders (user_id, id) ON DELETE RESTRICT
);

CREATE UNIQUE INDEX folders_one_root_per_user
    ON folders (user_id)
    WHERE parent_id IS NULL AND deleted_at IS NULL;

CREATE UNIQUE INDEX folders_unique_active_sibling_name
    ON folders (user_id, parent_id, lower(name))
    WHERE deleted_at IS NULL;

CREATE INDEX folders_user_parent_idx
    ON folders (user_id, parent_id)
    WHERE deleted_at IS NULL;

CREATE TABLE storage_objects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider TEXT NOT NULL,
    provider_object_key TEXT NOT NULL,
    size_bytes BIGINT NOT NULL,
    mime_type TEXT,
    sha256 CHAR(64),
    state TEXT NOT NULL DEFAULT 'uploading',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    modified_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT storage_objects_size_nonnegative CHECK (size_bytes >= 0),
    CONSTRAINT storage_objects_state CHECK (state IN ('uploading', 'ready', 'failed', 'deleting', 'deleted')),
    CONSTRAINT storage_objects_sha256_format CHECK (sha256 IS NULL OR sha256 ~ '^[0-9a-fA-F]{64}$'),
    UNIQUE (provider, provider_object_key),
    UNIQUE (user_id, id)
);

CREATE INDEX storage_objects_user_state_idx
    ON storage_objects (user_id, state);

CREATE TABLE files (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    folder_id UUID NOT NULL,
    storage_object_id UUID,
    name TEXT NOT NULL,
    mime_type TEXT,
    size_bytes BIGINT,
    sha256 CHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    modified_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT files_name_not_empty CHECK (length(trim(name)) > 0),
    CONSTRAINT files_name_not_path CHECK (name NOT LIKE '%/%' AND name NOT LIKE '%\\%'),
    CONSTRAINT files_name_not_dot CHECK (name NOT IN ('.', '..')),
    CONSTRAINT files_name_length CHECK (length(name) <= 255),
    CONSTRAINT files_size_nonnegative CHECK (size_bytes IS NULL OR size_bytes >= 0),
    CONSTRAINT files_sha256_format CHECK (sha256 IS NULL OR sha256 ~ '^[0-9a-fA-F]{64}$'),
    CONSTRAINT files_folder_same_user_fk
        FOREIGN KEY (user_id, folder_id) REFERENCES folders (user_id, id) ON DELETE RESTRICT,
    CONSTRAINT files_storage_object_same_user_fk
        FOREIGN KEY (user_id, storage_object_id) REFERENCES storage_objects (user_id, id) ON DELETE RESTRICT
);

CREATE UNIQUE INDEX files_unique_active_sibling_name
    ON files (user_id, folder_id, lower(name))
    WHERE deleted_at IS NULL;

CREATE INDEX files_user_folder_idx
    ON files (user_id, folder_id)
    WHERE deleted_at IS NULL;

CREATE INDEX files_storage_object_idx
    ON files (storage_object_id);

CREATE TABLE sync_changes (
    sequence BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_id UUID,
    operation_id UUID NOT NULL,
    entity_type TEXT NOT NULL,
    entity_id UUID NOT NULL,
    operation TEXT NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT sync_changes_entity_type CHECK (entity_type IN ('folder', 'file', 'storage_object')),
    CONSTRAINT sync_changes_operation CHECK (operation IN ('create', 'update', 'move', 'delete', 'restore')),
    UNIQUE (user_id, operation_id),
    CONSTRAINT sync_changes_device_same_user_fk
        FOREIGN KEY (user_id, device_id) REFERENCES devices (user_id, id) ON DELETE SET NULL
);

CREATE INDEX sync_changes_user_sequence_idx
    ON sync_changes (user_id, sequence);

CREATE INDEX sync_changes_entity_idx
    ON sync_changes (entity_type, entity_id, sequence);
