/**
 * DARKI Cloud storage-provider boundary.
 *
 * Application services depend on this contract, never on Telegram APIs.
 * Provider credentials and provider-specific SDKs stay behind implementations.
 */

export type StorageProviderName = "telegram" | (string & {});

export type StorageErrorCode =
  | "AUTHENTICATION"
  | "RATE_LIMITED"
  | "OBJECT_NOT_FOUND"
  | "OBJECT_TOO_LARGE"
  | "NETWORK"
  | "TEMPORARY"
  | "UNKNOWN";

export interface StorageCapabilities {
  maxUploadBytes: number | null;
  maxDownloadBytes: number | null;
  supportsStreamingUpload: boolean;
  supportsStreamingDownload: boolean;
  supportsRangeDownload: boolean;
  supportsServerSideCopy: boolean;
}

export interface PutObjectInput {
  /** Application-generated idempotency key. */
  idempotencyKey: string;
  filename: string;
  mimeType?: string;
  sizeBytes?: number;
  sha256?: string;
  /** The provider consumes this stream exactly once. */
  body: AsyncIterable<Uint8Array>;
}

export interface StoredObject {
  provider: StorageProviderName;
  objectKey: string;
  sizeBytes: number;
  mimeType?: string;
  sha256?: string;
  metadata?: Record<string, string>;
  createdAt: Date;
}

export interface GetObjectInput {
  objectKey: string;
  /** Optional byte range for providers that support it. */
  range?: {
    start: number;
    endInclusive?: number;
  };
}

export interface StoredObjectMetadata {
  provider: StorageProviderName;
  objectKey: string;
  sizeBytes: number;
  mimeType?: string;
  sha256?: string;
  metadata?: Record<string, string>;
  createdAt?: Date;
  modifiedAt?: Date;
}

export interface StorageObjectStream {
  body: AsyncIterable<Uint8Array>;
  metadata: StoredObjectMetadata;
}

export interface GetMetadataInput {
  objectKey: string;
}

export interface ExistsInput {
  objectKey: string;
}

export interface DeleteObjectInput {
  objectKey: string;
}

export interface StorageHealth {
  healthy: boolean;
  provider: StorageProviderName;
  checkedAt: Date;
  message?: string;
}

export class StorageProviderError extends Error {
  readonly code: StorageErrorCode;
  readonly retryable: boolean;

  constructor(
    code: StorageErrorCode,
    message: string,
    options: { retryable?: boolean } = {},
  ) {
    super(message);
    this.name = "StorageProviderError";
    this.code = code;
    this.retryable = options.retryable ?? false;
  }
}

export interface StorageProvider {
  readonly name: StorageProviderName;

  capabilities(): Promise<StorageCapabilities>;

  putObject(input: PutObjectInput): Promise<StoredObject>;

  getObject(input: GetObjectInput): Promise<StorageObjectStream>;

  getMetadata(
    input: GetMetadataInput,
  ): Promise<StoredObjectMetadata | null>;

  exists(input: ExistsInput): Promise<boolean>;

  /** Safe to retry when the object is already absent. */
  deleteObject(input: DeleteObjectInput): Promise<void>;

  healthCheck(): Promise<StorageHealth>;
}
