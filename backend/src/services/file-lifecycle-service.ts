import { randomUUID } from "node:crypto";
import { db } from "../db/pool.js";
import { FileRepository } from "../db/repositories/file-repository.js";
import { StorageObjectRepository } from "../db/repositories/storage-object-repository.js";
import { SyncChangeRepository } from "../db/repositories/sync-change-repository.js";
import type { StorageProvider } from "../storage/types.js";
import { StorageProviderError } from "../storage/provider-error.js";
import { NotFoundError, ServiceError } from "./errors.js";

export class FileLifecycleService {
  constructor(private readonly storage: StorageProvider) {}

  async delete(userId: string, fileId: string, deviceId?: string | null) {
    const files = new FileRepository(db);
    const objects = new StorageObjectRepository(db);
    const file = await files.findByIdForUser(fileId, userId);
    if (!file || file.deletedAt || !file.storageObjectId) throw new NotFoundError("File not found");
    const object = await objects.findByIdForUser(file.storageObjectId, userId);
    if (!object) throw new NotFoundError("File content not found");

    if (!await files.softDelete(fileId, userId)) throw new NotFoundError("File not found");
    await objects.setState(object.id, userId, "deleting");
    await new SyncChangeRepository(db).append({ userId, deviceId: deviceId ?? null, operationId: randomUUID(), entityType: "file", entityId: fileId, operation: "delete", payload: { name: file.name, folderId: file.folderId } });

    try {
      await this.storage.deleteObject({ providerObjectKey: object.providerObjectKey });
      await objects.setDeleted(object.id, userId);
      return { deleted: true };
    } catch (error) {
      if (error instanceof StorageProviderError && error.code === "OBJECT_NOT_FOUND") {
        await objects.setDeleted(object.id, userId);
        return { deleted: true };
      }
      if (error instanceof StorageProviderError) throw new ServiceError("Remote deletion failed; cleanup can be retried", "STORAGE_DELETE_FAILED", 503);
      throw error;
    }
  }

  async restore(userId: string, fileId: string, deviceId?: string | null) {
    const files = new FileRepository(db);
    const objects = new StorageObjectRepository(db);
    const file = await files.findByIdForUser(fileId, userId);
    if (!file || !file.deletedAt || !file.storageObjectId) throw new NotFoundError("Deleted file not found");
    const object = await objects.findByIdForUser(file.storageObjectId, userId);
    if (!object || object.state !== "ready") throw new NotFoundError("File content is no longer available");
    const restored = await files.restore(fileId, userId);
    if (!restored) throw new NotFoundError("Deleted file not found");
    await new SyncChangeRepository(db).append({ userId, deviceId: deviceId ?? null, operationId: randomUUID(), entityType: "file", entityId: fileId, operation: "restore", payload: { name: restored.name, folderId: restored.folderId } });
    return restored;
  }
}
