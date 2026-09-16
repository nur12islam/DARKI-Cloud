import { randomUUID } from "node:crypto";
import { Readable } from "node:stream";
import { db } from "../db/pool.js";
import { withTransaction } from "../db/transaction.js";
import { FileRepository } from "../db/repositories/file-repository.js";
import { FolderRepository } from "../db/repositories/folder-repository.js";
import { StorageObjectRepository } from "../db/repositories/storage-object-repository.js";
import { SyncChangeRepository } from "../db/repositories/sync-change-repository.js";
import { NotFoundError, ServiceError } from "./errors.js";
import type { StorageProvider } from "../storage/types.js";
import { StorageProviderError } from "../storage/provider-error.js";

const MAX_NAME_LENGTH = 255;

function validateName(name: string): string {
  const normalized = name.trim();
  if (!normalized || normalized === "." || normalized === ".." || /[\\/]/.test(normalized)) {
    throw new ServiceError("Invalid filename", "INVALID_INPUT", 400);
  }
  if (normalized.length > MAX_NAME_LENGTH) {
    throw new ServiceError("Filename is too long", "INVALID_INPUT", 400);
  }
  return normalized;
}

function mapStorageError(error: StorageProviderError): ServiceError {
  if (error.code === "OBJECT_TOO_LARGE") return new ServiceError(error.message, "OBJECT_TOO_LARGE", 413);
  if (error.code === "RATE_LIMITED") return new ServiceError("Storage provider rate limit reached", "STORAGE_RATE_LIMITED", 503);
  if (error.code === "AUTHENTICATION") return new ServiceError("Storage provider authentication failed", "STORAGE_AUTHENTICATION", 503);
  if (error.code === "OBJECT_NOT_FOUND") return new NotFoundError("Stored object not found");
  return new ServiceError("Storage provider request failed", "STORAGE_ERROR", 503);
}

export class FileService {
  constructor(private readonly storage: StorageProvider) {}

  async capabilities() {
    return this.storage.capabilities();
  }

  async upload(input: {
    userId: string;
    folderId: string;
    name: string;
    mimeType: string | null;
    sizeBytes: number;
    body: Readable;
    sha256?: string | null;
    deviceId?: string | null;
    operationId?: string;
  }) {
    const name = validateName(input.name);
    if (!Number.isSafeInteger(input.sizeBytes) || input.sizeBytes < 0) {
      throw new ServiceError("sizeBytes must be a non-negative safe integer", "INVALID_INPUT", 400);
    }

    const capabilities = await this.storage.capabilities();
    if (capabilities.maxUploadBytes !== null && input.sizeBytes > capabilities.maxUploadBytes) {
      throw new ServiceError("File exceeds storage provider upload limit", "OBJECT_TOO_LARGE", 413);
    }

    const folder = await new FolderRepository(db).findByIdForUser(input.folderId, input.userId);
    if (!folder || folder.deletedAt) throw new NotFoundError("Folder not found");

    const operationId = input.operationId ?? randomUUID();

    let stored;
    try {
      stored = await this.storage.putObject({
        body: input.body,
        sizeBytes: input.sizeBytes,
        mimeType: input.mimeType,
        filename: name,
        sha256: input.sha256 ?? null,
        idempotencyKey: operationId,
      });
    } catch (error) {
      if (error instanceof StorageProviderError) throw mapStorageError(error);
      throw error;
    }

    try {
      return await withTransaction(async (client) => {
        const files = new FileRepository(client);
        const objects = new StorageObjectRepository(client);
        const folders = new FolderRepository(client);
        const syncChanges = new SyncChangeRepository(client);

        const ownedFolder = await folders.findByIdForUser(input.folderId, input.userId);
        if (!ownedFolder || ownedFolder.deletedAt) throw new NotFoundError("Folder not found");

        const object = await objects.create({
          provider: stored.provider,
          providerObjectKey: stored.providerObjectKey,
          sizeBytes: String(stored.sizeBytes),
          mimeType: stored.mimeType,
          sha256: stored.sha256,
          state: "ready",
        });

        const file = await files.create({
          userId: input.userId,
          folderId: input.folderId,
          name,
          mimeType: stored.mimeType,
          sizeBytes: String(stored.sizeBytes),
          sha256: stored.sha256,
          storageObjectId: object.id,
        });

        await syncChanges.append({
          userId: input.userId,
          deviceId: input.deviceId ?? null,
          operationId,
          entityType: "file",
          entityId: file.id,
          operation: "create",
          payload: { folderId: file.folderId, name: file.name, sizeBytes: file.sizeBytes, mimeType: file.mimeType },
        });

        return file;
      });
    } catch (error) {
      try {
        await this.storage.deleteObject({ providerObjectKey: stored.providerObjectKey });
      } catch (cleanupError) {
        console.error("Failed to clean up orphaned storage object", cleanupError);
      }
      throw error;
    }
  }

  async getDownload(userId: string, fileId: string) {
    const file = await new FileRepository(db).findByIdForUser(fileId, userId);
    if (!file || file.deletedAt || !file.storageObjectId) throw new NotFoundError("File not found");

    const object = await new StorageObjectRepository(db).findById(file.storageObjectId);
    if (!object || object.deletedAt || object.state !== "ready") throw new NotFoundError("File content not available");

    if (object.provider !== this.storage.name) {
      throw new ServiceError("Storage provider is not available", "STORAGE_PROVIDER_UNAVAILABLE", 503);
    }

    const capabilities = await this.storage.capabilities();
    const sizeBytes = Number(object.sizeBytes);
    if (!Number.isSafeInteger(sizeBytes)) throw new ServiceError("Stored file size is invalid", "STORAGE_ERROR", 500);
    if (capabilities.maxDownloadBytes !== null && sizeBytes > capabilities.maxDownloadBytes) {
      throw new ServiceError("File exceeds storage provider download limit", "OBJECT_TOO_LARGE", 413);
    }

    try {
      const body = await this.storage.getObject({ providerObjectKey: object.providerObjectKey });
      return { file, object, body };
    } catch (error) {
      if (error instanceof StorageProviderError) throw mapStorageError(error);
      throw error;
    }
  }
}
