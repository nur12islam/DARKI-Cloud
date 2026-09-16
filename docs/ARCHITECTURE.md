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
┌──────────────┐ ┌────────────────────┐
│ Metadata DB  │ │ Telegram Storage   │
│              │ │ StorageProvider    │
│ users        │ │ Bot API initially  │
│ devices      │ └────────────────────┘
│ folders      │
│ files        │
│ storage_objs │
│ sync_changes │
└──────────────┘
```

The metadata database is the source of truth for the user's virtual filesystem. Telegram is a binary storage provider behind an abstraction layer.

## Android Client

Responsibilities:

- UI and navigation
- Local database/cache
- Android Storage Access Framework integration
- Local file previews
- Upload/download requests
- Offline operation queue
- Connectivity handling
- Secure local session storage
- Active-session sync connection when available

The client must not contain server-only secrets such as the Telegram bot token or database credentials.

The client must not depend on Google Play Services for correctness.

## Backend

Responsibilities:

- Authenticate users
- Authorize file ownership
- Maintain metadata
- Translate virtual filesystem operations into storage operations
- Manage Telegram communication through `StorageProvider`
- Issue upload/download operations
- Coordinate multi-device synchronization
- Validate requests
- Enforce provider capabilities and file-size limits
- Write an append-only synchronization change record for every user-visible mutation

## Database Model

The initial PostgreSQL schema is in `database/schema.sql`.

Core entities:

- `users` — DARKI Cloud accounts
- `devices` — installations and per-device sync cursors
- `folders` — virtual directory tree
- `files` — logical user-visible files
- `storage_objects` — provider-neutral binary storage references
- `sync_changes` — append-only change log and synchronization cursor source

The database does **not** store file bytes.

See `docs/DATA-MODEL.md` for invariants and lifecycle rules.

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

The folder relationships are stored in the metadata database. Telegram storage references belong to storage-object records and do not determine the user's folder hierarchy.

**Do not create one Telegram channel/chat per DARKI Cloud folder.** That would couple the logical filesystem to Telegram and make moves, renames, synchronization, and future provider changes unnecessarily difficult.

## Authentication Boundary

DARKI Cloud authentication and Telegram storage authentication are separate concerns.

The initial architecture will use Telegram Login for establishing the user's DARKI Cloud identity, with server-side verification. A dedicated storage bot is then used by the backend for the initial Telegram storage provider.

A future MTProto/TDLib provider may be added if the initial provider's capabilities are insufficient, but user-account authorization sessions must then be handled as highly sensitive server-side credentials.

## Storage Abstraction

Telegram is hidden behind a provider interface so another storage backend can be added later without redesigning the application.

Conceptually:

```text
StorageProvider
├── putObject()
├── getObject()
├── deleteObject()
├── objectExists()
├── getMetadata()
└── healthCheck()
```

Initial implementation:

```text
TelegramBotStorage implements StorageProvider
```

The backend must expose provider capabilities such as maximum supported object size rather than hard-coding Telegram limits into the Android UI.

See `docs/TELEGRAM-STORAGE.md` for the Telegram-specific decision and limitations.

## Sync Strategy

The initial implementation uses a server-maintained monotonically increasing change sequence/cursor in `sync_changes`.

Conceptually:

```text
Device A → mutation → API → DB
                         ↓
                  sync_changes
                         ↓
Device B ← cursor-based API ← DB
```

A device only advances its local cursor after successfully applying the corresponding changes.

Use idempotency keys for operations that could otherwise create duplicate objects when a network retry occurs.

For GApps-free operation:

- synchronize immediately while the app is active;
- use an active WebSocket/SSE connection when appropriate;
- use Android WorkManager for deferred/background synchronization;
- never make Firebase/FCM the source of truth.

## Offline Strategy

The client maintains:

- Local metadata database
- Downloaded-file cache
- Pending operation queue
- Last successful sync cursor

Connectivity restoration triggers queued synchronization. Server responses determine the authoritative result of each operation.

Conflict behavior will be explicitly designed before offline mutations are enabled; it will not be left to accidental last-write-wins behavior.

## Security Principles

- TLS for network traffic
- Server-side authorization
- No secrets in APK or Git history
- Secure local credential/session storage
- Validate file ownership on every protected operation
- Never trust client-provided user IDs
- Avoid logging authentication secrets or storage credentials
- Keep Telegram bot credentials backend-only
- Treat any future Telegram user-session authorization material as highly sensitive

## Development Sequence

1. Architecture and provider validation
2. Database/data model
3. Storage provider contract
4. Backend API skeleton
5. Authentication
6. Android project shell
7. Local data/cache layer
8. End-to-end upload/download vertical slice
9. Virtual filesystem operations
10. Cursor-based multi-device sync
11. Offline queue and conflict handling
12. Photos and media previews
13. Search/indexing
14. Security hardening
15. Performance and Liquid Glass UI polish

The first implementation milestone should prove one complete path — authenticate → create/list folder → upload → persist metadata → store binary → list → download → preview — before broad feature expansion.
