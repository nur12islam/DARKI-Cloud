import { randomUUID } from "node:crypto";
import { db } from "../db/pool.js";
import { withTransaction } from "../db/transaction.js";
import { FileRepository } from "../db/repositories/file-repository.js";
import { StorageObjectRepository } from "../db/repositories/storage-object-repository.js";
import { SyncChangeRepository } from "../db/repositories/sync-change-repository.js";
import type { StorageProvider } from "../storage/types.js";

export class StorageReconciliationService {
  constructor(private readonly storage: StorageProvider) {}

  async run(limit = 100): Promise<{ checked: number; missing: number }> {
    const objects = await new StorageObjectRepository(db).listReady(limit);
    let missing = 0;

    for (const object of objects) {
      try {
        if (await this.storage.exists({ providerObjectKey: object.providerObjectKey })) continue;
      } catch (error) {
        console.error("Storage reconciliation skipped object after provider error", object.id, error);
        continue;
      }

      await withTransaction(async (client) => {
        const current = await new StorageObjectRepository(client).findByIdForUser(object.id, object.userId);
        if (!current || current.state !== "ready") return;

        const file = await new FileRepository(client).softDeleteByStorageObject(object.id, object.userId);
        await new StorageObjectRepository(client).setDeleted(object.id, object.userId);
        if (file) {
          await new SyncChangeRepository(client).append({
            userId: object.userId,
            operationId: randomUUID(),
            entityType: "file",
            entityId: file.id,
            operation: "delete",
            payload: {
              name: file.name,
              folderId: file.folderId,
              reconciled: true,
            },
          });
        }
      });
      missing += 1;
    }

    return { checked: objects.length, missing };
  }
}
