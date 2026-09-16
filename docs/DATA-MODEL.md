# DARKI Cloud — Data Model

Status: **Milestone 1 — design locked**

## Purpose

The database stores the authoritative virtual filesystem and synchronization state. It does **not** store file bytes.

File bytes belong to a `StorageProvider` implementation. For the initial provider, those bytes are stored in Telegram and referenced through `storage_objects`.

## Core entities

```text
users
  │
  ├── devices
  │
  ├── folders ──── folders (parent/child)
  │
  ├── files ─────── storage_objects
  │
  └── sync_changes
```

### `users`

Represents a DARKI Cloud account.

Important rule: the backend derives the authenticated user from the server-side session/token. Client requests must never be trusted to select another `user_id`.

### `devices`

Represents an installation/device participating in synchronization.

A device has its own sync cursor so two devices can independently catch up.

### `folders`

Represents the virtual filesystem hierarchy.

`parent_id` is nullable only for the single root folder of a user.

Telegram chats/channels are not represented here.

### `storage_objects`

Provider-neutral representation of a stored binary object.

The table deliberately avoids Telegram-specific columns such as `telegram_message_id`. Provider-specific identifiers belong inside the provider implementation or a provider metadata structure.

### `files`

Represents a logical file visible to the user.

A file belongs to exactly one folder and optionally references one storage object while an upload is pending.

### `sync_changes`

An append-only server change log.

`sequence` is globally increasing and is used as the synchronization cursor. A device asks for changes after its last acknowledged sequence.

## Deletion model

Folders and files use soft deletion through `deleted_at`.

Deletion creates a synchronization event instead of immediately destroying the database row. This allows another device that was offline to learn that the object was deleted.

Physical storage deletion can happen after the metadata operation is durably recorded and the storage provider confirms deletion.

## Rename/move semantics

A rename changes `name` and creates a change-log entry.

A move changes `folder_id` and creates a change-log entry.

These are metadata operations; they must not cause a Telegram object to be moved between chats.

## Naming rules

Names are user-visible strings, not paths.

The backend will reject:

- empty names
- `.` and `..`
- names containing `/` or `\\`
- names exceeding the configured maximum length
- duplicate names within the same parent folder

The initial implementation uses case-insensitive sibling uniqueness through PostgreSQL `lower(name)` indexes.

## File integrity

For uploaded files, the backend records:

- byte size
- MIME type
- SHA-256 checksum

The checksum is useful for verifying downloads, detecting corruption, and making future deduplication possible. It is not used as the primary file identity.

## Storage lifecycle

```text
created
   ↓
uploading
   ↓
ready
   │
   ├── failed
   │
   └── deleting → deleted
```

The exact state machine is implemented by the backend/storage milestone; the database must be able to represent incomplete uploads without exposing them as normal ready files.

## Synchronization invariants

1. Every user-visible mutation is associated with a `sync_changes` record.
2. `sync_changes.sequence` never decreases.
3. A device cursor only advances after the corresponding changes have been successfully applied locally.
4. Deleted objects remain represented until the retention/compaction policy allows removal.
5. Storage-provider failures must not silently report a successful upload.
6. Retries must use an idempotency key where the operation could otherwise create duplicate objects.

## Deliberately deferred

The following are **not** part of this schema yet:

- sharing permissions
- public links
- full-text search indexes
- photo EXIF index
- thumbnails
- favorites
- albums
- trash retention jobs
- billing/quotas
- encryption-at-rest beyond provider/database infrastructure

They will be added only when their behavior is specified.
