# DARKI Cloud — Telegram Storage Decision

Status: **Architecture decision — before implementation**

## 1. Decision

DARKI Cloud will treat Telegram as a **remote binary storage provider**, not as the application's filesystem.

The DARKI Cloud metadata database remains authoritative for:

- folders
- file names
- parent folders
- logical paths
- ownership
- timestamps
- MIME types
- sizes
- favorites/albums/tags
- sync versions
- Telegram storage references

The Android client will never depend on Telegram's chat/channel structure to represent folders.

## 2. Telegram integration options investigated

### Option A — Telegram Bot API

The Bot API is simple to operate from a backend and keeps the bot token server-side. Telegram currently documents a 50 MB limit for files sent by bots through the standard Bot API, while `getFile` currently supports downloads up to 20 MB. Telegram also documents a local Bot API server that can upload up to 2000 MB and download without a size limit.

This makes the standard hosted Bot API suitable for an MVP with a deliberately limited file-size policy, but it is not sufficient by itself for an unrestricted Google Drive replacement.

### Option B — Telegram user account through MTProto / TDLib

Telegram's MTProto user authorization supports third-party client applications, and TDLib is Telegram's official cross-platform client library. TDLib handles networking, encryption, local data storage, and ordered updates.

This provides much deeper access to Telegram as a user client, but it introduces a substantially more sensitive authentication/session architecture. A user's Telegram authorization key/session must be treated as highly sensitive material.

### Option C — Telegram Login + Bot storage

Telegram Login is an authentication mechanism for identifying the Telegram user to the DARKI Cloud backend. The official login documentation describes verification of the returned identity data using the bot token as the server-side secret.

This is useful for **DARKI Cloud authentication**, but Telegram Login does not by itself turn the user's Telegram account into a generic cloud filesystem. Storage still needs a separate Telegram storage identity/provider.

## 3. Chosen architecture for the first implementation

For the first implementation, DARKI Cloud will use:

```text
Android app
    │
    │ HTTPS
    ▼
DARKI Cloud backend
    │
    ├── Authentication / authorization
    ├── Metadata database
    ├── Sync engine
    └── Telegram StorageProvider
             │
             ▼
        Telegram Bot API
```

The first version will use a **dedicated Telegram bot and private storage chat/channel** as the binary backend. The storage chat/channel is an implementation detail and must never be exposed as the user's folder hierarchy.

A future `TelegramMtprotoStorageProvider` can be added if the Bot API limits become a real product constraint.

## 4. Why we are not using one Telegram channel per folder

A Telegram channel per DARKI Cloud folder would make Telegram's structure the filesystem. That creates unnecessary synchronization, rename/move complexity, channel-management overhead, and coupling to Telegram.

Instead:

```text
DARKI Cloud:
Documents/
  College/
    Assignment.pdf
Photos/
  2026/
    IMG_001.jpg

Telegram:
  opaque storage messages/files
```

The database maps each logical DARKI Cloud file to its Telegram storage reference.

## 5. StorageProvider abstraction

The backend will expose an internal interface conceptually equivalent to:

```text
StorageProvider
├── putObject()
├── getObject()
├── deleteObject()
├── objectExists()
├── getMetadata()
└── healthCheck()
```

The rest of DARKI Cloud must not know whether the object is stored in Telegram, another object store, or a future provider.

## 6. File-size policy

The application must not pretend that Telegram provides unlimited Drive-style storage.

The backend will enforce an explicit configurable maximum file size for the active provider. The Android UI will obtain this capability from the backend rather than hard-coding an assumption.

If the project later moves to a local Telegram Bot API server or MTProto/TDLib storage, the provider capability can change without redesigning the virtual filesystem.

## 7. Security rules

Never commit any of the following to GitHub:

- Telegram bot token
- Telegram user authorization/session data
- database passwords
- backend signing secrets
- JWT signing secrets
- production credentials
- private storage-chat identifiers if they are being treated as secrets by the deployment

The Android APK must not contain the Telegram bot token or backend-only credentials.

The backend authenticates the user, authorizes the requested file operation, and then talks to Telegram.

## 8. Authentication decision

DARKI Cloud authentication and Telegram storage authentication are separate concerns.

The initial design will use Telegram Login to establish the user's DARKI Cloud identity, with server-side verification. Storage operations will use the dedicated storage bot independently.

This separation prevents a user's DARKI Cloud account from being directly coupled to a Telegram user-session key.

## 9. GApps-free synchronization

The Lenovo Tab 6 does not have Google Play Services, so the first implementation must not depend on Firebase Cloud Messaging for correctness.

Synchronization will use:

1. immediate sync while the app is active;
2. an HTTPS/WebSocket or SSE connection while the app is active when appropriate;
3. Android WorkManager for deferred/background synchronization;
4. a server-side monotonically increasing change cursor;
5. local queued operations for offline changes.

Push notifications may be added later as an optional enhancement, but they will never be the source of truth for synchronization.

## 10. Consequence for the MVP

The first milestone after architecture validation will not attempt to build every Google Drive feature.

The smallest useful end-to-end slice is:

```text
Telegram Login
      ↓
DARKI Cloud account
      ↓
Root folder
      ↓
Create folder
      ↓
Upload a supported file
      ↓
Metadata stored in DB
      ↓
Binary stored in Telegram
      ↓
List file
      ↓
Download file
      ↓
Open local preview
```

Only after this vertical slice is reliable should we expand into Photos, advanced previews, offline conflict handling, sharing, search indexing, and richer sync.

## Official Telegram references

- Telegram Login: https://core.telegram.org/bots/telegram-login
- Telegram Login Widget: https://core.telegram.org/widgets/login/
- Telegram Bot API: https://core.telegram.org/bots/api
- Telegram User Authorization: https://core.telegram.org/api/auth
- Telegram TDLib: https://core.telegram.org/tdlib
- TDLib getting started: https://core.telegram.org/tdlib/getting-started
