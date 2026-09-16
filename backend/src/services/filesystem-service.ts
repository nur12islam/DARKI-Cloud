import { randomUUID } from "node:crypto";
import { db } from "../db/pool.js";
import { withTransaction } from "../db/transaction.js";
import { DeviceRepository } from "../db/repositories/device-repository.js";
import { FileRepository } from "../db/repositories/file-repository.js";
import { FolderRepository } from "../db/repositories/folder-repository.js";
import { SyncChangeRepository } from "../db/repositories/sync-change-repository.js";
import { ConflictError, NotFoundError, ServiceError } from "./errors.js";

const MAX_NAME_LENGTH = 255;

function validateName(name: string): string {
  const normalized = name.trim();
  if (!normalized || normalized === "." || normalized === ".." || /[\\/]/.test(normalized)) throw new ServiceError("Invalid filesystem name", "INVALID_INPUT", 400);
  if (normalized.length > MAX_NAME_LENGTH) throw new ServiceError("Filesystem name is too long", "INVALID_INPUT", 400);
  return normalized;
}

function isUniqueViolation(error: unknown): boolean {
  return typeof error === "object" && error !== null && "code" in error && (error as { code?: string }).code === "23505";
}

export class FilesystemService {
  async listFolder(userId: string, folderId: string) {
    const folders = new FolderRepository(db); const files = new FileRepository(db);
    const folder = await folders.findByIdForUser(folderId, userId);
    if (!folder || folder.deletedAt) throw new NotFoundError("Folder not found");
    const [children, childFiles] = await Promise.all([folders.listChildren(userId, folderId), files.listByFolder(userId, folderId)]);
    return { folder, folders: children, files: childFiles };
  }

  async createFolder(userId: string, parentId: string, name: string, deviceId?: string | null) {
    const normalizedName = validateName(name);
    return withTransaction(async (client) => {
      const folders = new FolderRepository(client); const syncChanges = new SyncChangeRepository(client);
      const parent = await folders.findByIdForUser(parentId, userId);
      if (!parent || parent.deletedAt) throw new NotFoundError("Parent folder not found");
      if (deviceId && !(await new DeviceRepository(client).findByIdForUser(deviceId, userId))) throw new NotFoundError("Device not found");
      try {
        const folder = await folders.create(userId, parentId, normalizedName);
        await syncChanges.append({ userId, deviceId: deviceId ?? null, operationId: randomUUID(), entityType: "folder", entityId: folder.id, operation: "create", payload: { parentId, name: folder.name } });
        return folder;
      } catch (error) { if (isUniqueViolation(error)) throw new ConflictError("A folder with this name already exists"); throw error; }
    });
  }

  async renameFolder(userId: string, folderId: string, name: string, deviceId?: string | null) {
    const normalizedName = validateName(name);
    return withTransaction(async (client) => {
      const folders = new FolderRepository(client); const sync = new SyncChangeRepository(client);
      const existing = await folders.findByIdForUser(folderId, userId);
      if (!existing || existing.deletedAt) throw new NotFoundError("Folder not found");
      if (deviceId && !(await new DeviceRepository(client).findByIdForUser(deviceId, userId))) throw new NotFoundError("Device not found");
      try {
        const folder = await folders.rename(folderId, userId, normalizedName);
        if (!folder) throw new NotFoundError("Folder not found");
        await sync.append({ userId, deviceId: deviceId ?? null, operationId: randomUUID(), entityType: "folder", entityId: folderId, operation: "update", payload: { name: folder.name } });
        return folder;
      } catch (error) { if (isUniqueViolation(error)) throw new ConflictError("A folder with this name already exists"); throw error; }
    });
  }

  async moveFolder(userId: string, folderId: string, parentId: string, deviceId?: string | null) {
    return withTransaction(async (client) => {
      const folders = new FolderRepository(client); const sync = new SyncChangeRepository(client);
      const folder = await folders.findByIdForUser(folderId, userId);
      const parent = await folders.findByIdForUser(parentId, userId);
      if (!folder || folder.deletedAt) throw new NotFoundError("Folder not found");
      if (!parent || parent.deletedAt) throw new NotFoundError("Destination folder not found");
      if (folderId === parentId) throw new ConflictError("A folder cannot be moved into itself");
      if (deviceId && !(await new DeviceRepository(client).findByIdForUser(deviceId, userId))) throw new NotFoundError("Device not found");
      try {
        const moved = await folders.move(folderId, userId, parentId);
        if (!moved) throw new NotFoundError("Folder not found");
        await sync.append({ userId, deviceId: deviceId ?? null, operationId: randomUUID(), entityType: "folder", entityId: folderId, operation: "move", payload: { parentId } });
        return moved;
      } catch (error) { if (isUniqueViolation(error)) throw new ConflictError("A folder with this name already exists"); throw error; }
    });
  }

  async renameFile(userId: string, fileId: string, name: string, deviceId?: string | null) {
    const normalizedName = validateName(name);
    return withTransaction(async (client) => {
      const files = new FileRepository(client); const sync = new SyncChangeRepository(client);
      const existing = await files.findByIdForUser(fileId, userId);
      if (!existing || existing.deletedAt) throw new NotFoundError("File not found");
      if (deviceId && !(await new DeviceRepository(client).findByIdForUser(deviceId, userId))) throw new NotFoundError("Device not found");
      try {
        const file = await files.rename(fileId, userId, normalizedName);
        if (!file) throw new NotFoundError("File not found");
        await sync.append({ userId, deviceId: deviceId ?? null, operationId: randomUUID(), entityType: "file", entityId: fileId, operation: "update", payload: { name: file.name } });
        return file;
      } catch (error) { if (isUniqueViolation(error)) throw new ConflictError("A file with this name already exists"); throw error; }
    });
  }

  async moveFile(userId: string, fileId: string, folderId: string, deviceId?: string | null) {
    return withTransaction(async (client) => {
      const files = new FileRepository(client); const folders = new FolderRepository(client); const sync = new SyncChangeRepository(client);
      const file = await files.findByIdForUser(fileId, userId); const folder = await folders.findByIdForUser(folderId, userId);
      if (!file || file.deletedAt) throw new NotFoundError("File not found");
      if (!folder || folder.deletedAt) throw new NotFoundError("Destination folder not found");
      if (deviceId && !(await new DeviceRepository(client).findByIdForUser(deviceId, userId))) throw new NotFoundError("Device not found");
      try {
        const moved = await files.move(fileId, userId, folderId);
        if (!moved) throw new NotFoundError("File not found");
        await sync.append({ userId, deviceId: deviceId ?? null, operationId: randomUUID(), entityType: "file", entityId: fileId, operation: "move", payload: { folderId } });
        return moved;
      } catch (error) { if (isUniqueViolation(error)) throw new ConflictError("A file with this name already exists"); throw error; }
    });
  }

  async registerDevice(userId: string, deviceName: string, platform: string) {
    const devices = new DeviceRepository(db); return devices.create(userId, deviceName.trim(), platform.trim());
  }
}
