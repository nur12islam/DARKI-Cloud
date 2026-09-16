# DARKI Cloud — Storage Provider Contract

## Purpose

The backend must not depend directly on Telegram-specific storage behavior.

All remote binary storage is accessed through a provider contract. The first implementation is `TelegramBotStorage`.

```text
Application services
        │
        ▼
 StorageProvider
        │
   ┌────┴─────┐
   ▼          ▼
Telegram   Future provider
```

## Provider responsibilities

A provider is responsible for binary-object operations only:

- accept an upload stream;
- return an opaque provider object key;
- download an object as a stream;
- delete an object;
- check existence;
- return provider metadata;
- report supported capabilities;
- perform a health check.

The provider must not know about DARKI Cloud folders, users, albums, search, or device synchronization.

## Conceptual TypeScript contract

```ts
export interface StorageProvider {
  readonly name: string;

  capabilities(): Promise<StorageCapabilities>;

  putObject(input: PutObjectInput): Promise<StoredObject>;

  getObject(input: GetObjectInput): Promise<ReadableStreamLike>;

  getMetadata(input: GetMetadataInput): Promise<StoredObjectMetadata | null>;

  exists(input: ExistsInput): Promise<boolean>;

  deleteObject(input: DeleteObjectInput): Promise<void>;

  healthCheck(): Promise<StorageHealth>;
}
```

The exact TypeScript types will be implemented in the backend milestone. This document defines the behavioral contract first.

## Upload contract

An upload receives:

- a readable stream;
- byte size when known;
- MIME type;
- original filename;
- optional SHA-256 checksum;
- an idempotency key supplied by the application service.

The provider returns:

- provider name;
- opaque object key;
- stored size;
- checksum when available;
- provider metadata needed for later retrieval;
- creation timestamp.

The provider must not decide the DARKI Cloud filename or folder.

## Download contract

The application supplies an opaque provider object key.

The provider returns a stream suitable for HTTP response streaming.

The application layer remains responsible for authorization. A provider must never be treated as the authorization boundary.

## Delete contract

Deletion must be safe to retry.

If the provider reports that the object is already absent, the application should be able to treat the operation as successfully completed.

## Idempotency

Uploads must have an application-level idempotency key.

Example:

```text
upload operation
      │
      ├── operation_id = UUID
      ▼
StorageProvider
      │
      └── provider object
```

A network timeout must not cause the backend to blindly upload the same binary multiple times.

The exact retry/reconciliation mechanism will be finalized with the backend implementation.

## Capabilities

The provider must report capabilities instead of making the Android application hard-code Telegram limits.

Conceptually:

```ts
interface StorageCapabilities {
  maxUploadBytes: number | null;
  maxDownloadBytes: number | null;
  supportsStreamingUpload: boolean;
  supportsStreamingDownload: boolean;
  supportsRangeDownload: boolean;
  supportsServerSideCopy: boolean;
}
```

`null` means no provider-level limit is advertised by the adapter; it does not mean the overall DARKI Cloud product has unlimited storage.

For the hosted Telegram Bot API, the adapter must expose the current documented limitations rather than pretending arbitrary-size objects are supported.

## Telegram implementation

The first provider will be:

```text
TelegramBotStorage
        │
        ▼
Telegram Bot API
```

The backend keeps the bot token outside source control and runtime configuration is injected through the deployment environment.

The provider stores enough Telegram information to retrieve the binary later, but those Telegram-specific identifiers stay behind the provider boundary as much as practical.

## Telegram object mapping

A DARKI Cloud `storage_objects` record represents the provider-independent object.

Conceptually:

```text
storage_objects
├── id                  ← DARKI Cloud ID
├── provider            ← "telegram"
├── provider_object_key ← provider-specific opaque key
├── size_bytes
├── mime_type
└── sha256
```

The provider-specific key can internally encode the Telegram storage chat/message/file identifiers. Application services should not parse it.

## File size and download behavior

The hosted Telegram Bot API currently documents up to 50 MB for bot uploads via multipart form data, while `getFile` downloads are currently limited to 20 MB. Telegram also documents a local Bot API server with up to 2000 MB uploads and unlimited downloads.

Therefore the provider contract must distinguish **upload capability** from **download capability**.

For the initial hosted provider, the backend should reject unsupported downloads before starting an expensive transfer and should expose the capability to the client.

A future local-Bot-API provider can implement the same interface without changing the filesystem model.

## Failure model

Provider errors should be classified into stable categories:

```text
ProviderError
├── AUTHENTICATION
├── RATE_LIMITED
├── OBJECT_NOT_FOUND
├── OBJECT_TOO_LARGE
├── NETWORK
├── TEMPORARY
└── UNKNOWN
```

Application code must not depend on Telegram's raw error strings.

## Security boundary

The provider may access storage credentials, but only backend code may call it.

Never expose:

- Telegram bot token;
- provider credentials;
- provider-specific storage secrets;
- raw Telegram API credentials

to the Android client.

## Future providers

Possible future implementations include:

```text
StorageProvider
├── TelegramBotStorage
├── TelegramLocalBotApiStorage
├── TelegramMtprotoStorage
└── OtherObjectStorage
```

No future provider is required for the current milestone.
