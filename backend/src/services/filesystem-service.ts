import { randomUUID } from "node:crypto";
import { withTransaction } from "../db/transaction.js";
import { DeviceRepository } from "../db/repositories/device-repository.js";
import { FileRepository } from "../db/repositories/file-repository.js";
import { FolderRepository } from "../db/repositories/folder-repository.js";
import { SyncChangeRepository } from "../db/repositories/sync-change-repository.js";
import { NotFoundError } from "./errors.js";

const MAX_NAME_LENGTH = 255;

function validateName(name: string): string {
  const normalized = name.trim();
  if (!normalized || normalized === "." || normalized === ".." || /[\\/]/.test(normalized)) {
    throw new Error("Invalid filesystem name");
  }
  if (normalized.length > MAX_NAME_LENGTH) {
    throw new Error("Filesystem name is too long");
  }
  return normalized;
}

export class FilesystemService {
  async listFolder(userId: string, folderId: string) {
    const folderRepository = new FolderRepository((await import("../db/pool.js")).db);
    const fileRepository = new FileRepository((await import("../db/pool.js")).db);
    const folder = await folderRepository.findByIdForUser(folderId, userId);
    if (!folder || folder.deletedAt) throw new NotFoundError("Folder not found");

    const [folders, files] = await Promise.all([
      folderRepository.listChildren(userId, folderId),
      fileRepository.listByFolder(userId, folderId),
    ]);
    return { folder, folders, files };
  }

  async createFolder(userId: string, parentId: string, name: string, deviceId?: string | null) {
    const normalizedName = validateName(name);

    return withTransaction(async (client) => {
      const folders = new FolderRepository(client);
      const syncChanges = new SyncChangeRepository(client);
      const parent = await folders.findByIdForUser(parentId, userId);
      if (!parent || parent.deletedAt) throw new NotFoundError("Parent folder not found");

      const folder = await folders.create(userId, parentId, normalizedName);
      await syncChanges.append({
        userId,
        deviceId,
        operationId: randomUUID(),
        entityType: "folder",
        entityId: folder.id,
        operation: "create",
        payload: { parentId, name: folder.name },
      });
      return folder;
    });
  }

  async registerDevice(userId: string, deviceName: string, platform: string) {
    const devices = new DeviceRepository((await import("../db/pool.js")).db);
    return devices.create(userId, deviceName.trim(), platform.trim());
  }
}
