-- DARKI Cloud — PostgreSQL schema
-- Milestone 1: authoritative virtual filesystem + sync state
--
-- This file intentionally contains no credentials or environment-specific values.

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
    parent_id UUID REFERENCES folders(id) ON DELETE RESTRICT,
    name TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    modified_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT folders_name_not_empty CHECK (length(trim(name)) > 0),
    CONSTRAINT folders_name_not_path CHECK (name NOT LIKE '%/%' AND name NOT LIKE '%\\%'),
    CONSTRAINT folders_name_not_dot CHECK (name NOT IN ('.', '..')),
    CONSTRAINT folders_name_length CHECK (length(name) <= 255)
);

-- Exactly one active root folder per user.
CREATE UNIQUE INDEX folders_one_root_per_user
    ON folders (user_id)
    WHERE parent_id IS NULL AND deleted_at IS NULL;

-- Names must be unique among active siblings, case-insensitively.
CREATE UNIQUE INDEX folders_unique_active_sibling_name
    ON folders (user_id, parent_id, lower(name))
    WHERE deleted_at IS NULL;

CREATE INDEX folders_user_parent_idx
    ON folders (user_id, parent_id)
    WHERE deleted_at IS NULL;

CREATE TABLE storage_objects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
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
    UNIQUE (provider, provider_object_key)
);

CREATE INDEX storage_objects_state_idx
    ON storage_objects (state);

CREATE TABLE files (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    folder_id UUID NOT NULL REFERENCES folders(id) ON DELETE RESTRICT,
    storage_object_id UUID REFERENCES storage_objects(id) ON DELETE RESTRICT,
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
    CONSTRAINT files_sha256_format CHECK (sha256 IS NULL OR sha256 ~ '^[0-9a-fA-F]{64}$')
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
    device_id UUID REFERENCES devices(id) ON DELETE SET NULL,
    operation_id UUID NOT NULL,
    entity_type TEXT NOT NULL,
    entity_id UUID NOT NULL,
    operation TEXT NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT sync_changes_entity_type CHECK (entity_type IN ('folder', 'file', 'storage_object')),
    CONSTRAINT sync_changes_operation CHECK (operation IN ('create', 'update', 'move', 'delete', 'restore')),
    UNIQUE (user_id, operation_id)
);

CREATE INDEX sync_changes_user_sequence_idx
    ON sync_changes (user_id, sequence);

CREATE INDEX sync_changes_entity_idx
    ON sync_changes (entity_type, entity_id, sequence);

-- Prevent a file from accidentally referencing another user's storage object
-- at the application level. The backend must additionally enforce ownership
-- in every mutation transaction.

-- Recommended initial bootstrap transaction:
--
-- INSERT INTO users (...) ...;
-- INSERT INTO folders (user_id, parent_id, name) VALUES (<user>, NULL, 'My Drive');
--
-- Root creation must be performed in the same transaction as account creation.
