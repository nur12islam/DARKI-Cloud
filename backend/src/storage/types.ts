import type { Readable } from "node:stream";

export type StorageCapabilities = {
  maxUploadBytes: number | null;
  maxDownloadBytes: number | null;
  supportsStreamingUpload: boolean;
  supportsStreamingDownload: boolean;
  supportsRangeDownload: boolean;
  supportsServerSideCopy: boolean;
};

export type PutObjectInput = {
  body: Readable;
  sizeBytes: number;
  mimeType: string | null;
  filename: string;
  sha256?: string | null;
  idempotencyKey: string;
};

export type StoredObject = {
  provider: string;
  providerObjectKey: string;
  sizeBytes: number;
  mimeType: string | null;
  sha256: string | null;
  createdAt: Date;
};

export type GetObjectInput = { providerObjectKey: string };
export type GetMetadataInput = { providerObjectKey: string };
export type ExistsInput = { providerObjectKey: string };
export type DeleteObjectInput = { providerObjectKey: string };

export type StoredObjectMetadata = {
  providerObjectKey: string;
  sizeBytes: number;
  mimeType: string | null;
  filename: string | null;
  sha256: string | null;
};

export type StorageHealth = { ok: boolean; latencyMs: number };

export type StorageProvider = {
  readonly name: string;
  capabilities(): Promise<StorageCapabilities>;
  putObject(input: PutObjectInput): Promise<StoredObject>;
  getObject(input: GetObjectInput): Promise<Readable>;
  getMetadata(input: GetMetadataInput): Promise<StoredObjectMetadata | null>;
  exists(input: ExistsInput): Promise<boolean>;
  deleteObject(input: DeleteObjectInput): Promise<void>;
  healthCheck(): Promise<StorageHealth>;
};
