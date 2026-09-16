# DARKI Cloud — Technical Architecture

## High-Level Model

```text
┌──────────────────────────┐
│     Android Client       │
│ Lenovo Tab 6 / Phone     │
└────────────┬─────────────┘
             │ HTTPS
             ▼
┌──────────────────────────┐
│     DARKI Cloud API      │
│ Auth · Files · Sync      │
└───────┬──────────┬───────┘
        │          │
        ▼          ▼
┌────────────┐ ┌──────────────┐
│ Metadata DB│ │Telegram Layer│
│            │ │              │
│ users      │ │ file objects │
│ folders    │ │ messages     │
│ files      │ │ storage refs │
│ devices    │ └──────────────┘
│ sync_ops   │
└────────────┘
```

## Android Client

Responsibilities:

- UI and navigation
- Local database/cache
- Android file picker integration
- Local file previews
- Upload/download requests
- Sync queue
- Connectivity handling
- Secure local session storage

The client must not contain server-only secrets.

## Backend

Responsibilities:

- Authenticate users
- Authorize file ownership
- Maintain metadata
- Translate virtual filesystem operations into storage operations
- Manage Telegram communication
- Issue download/upload operations
- Coordinate multi-device synchronization
- Validate requests

## Metadata Model

### users

- id
- telegram_user_id
- display_name
- created_at

### devices

- id
- user_id
- device_name
- platform
- last_seen
- sync_cursor

### folders

- id
- user_id
- parent_id
- name
- created_at
- modified_at

### files

- id
- user_id
- folder_id
- name
- mime_type
- size
- checksum
- telegram_chat_id
- telegram_message_id
- telegram_file_id
- created_at
- modified_at
- deleted_at

### sync_operations

- id
- user_id
- device_id
- operation
- object_id
- state
- created_at
- completed_at

## Virtual Filesystem

Example:

```text
My Drive
├── Documents
│   └── University
│       └── Assignment.pdf
└── Photos
    └── 2026
        └── IMG001.jpg
```

The folder relationships are stored in the metadata database. Telegram storage references are attached to file records.

## Sync Strategy

The initial implementation should use a server-maintained change sequence/cursor. Devices request changes after their last known cursor.

Conceptually:

```text
Device A → mutation → API → DB
                         ↓
                    change log
                         ↓
Device B ← sync cursor ← API
```

Use idempotent operations where possible so retries do not duplicate files or metadata.

## Offline Strategy

The client maintains:

- Local metadata database
- Downloaded-file cache
- Pending operation queue
- Last successful sync cursor

Connectivity restoration triggers queued synchronization.

## Storage Abstraction

Telegram should be hidden behind a storage interface so another storage provider can be added later without redesigning the entire application.

Conceptually:

```text
StorageProvider
├── upload()
├── download()
├── delete()
└── getMetadata()

TelegramStorage implements StorageProvider
```

## Security Principles

- TLS for network traffic
- Server-side authorization
- No secrets in APK or Git history
- Secure local credential/session storage
- Validate file ownership on every protected operation
- Never trust client-provided user IDs
- Avoid logging authentication secrets or storage credentials

## Development Sequence

1. Android shell and UI
2. Local data model
3. Local file browser and preview
4. Backend skeleton
5. Telegram authentication
6. Telegram storage adapter
7. Upload/download
8. Virtual filesystem
9. Sync engine
10. Photos
11. Offline mode
12. Security hardening
13. Performance and UI polish
