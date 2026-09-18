import { db } from "../db/pool.js";
import { DeviceRepository } from "../db/repositories/device-repository.js";
import { SyncChangeRepository } from "../db/repositories/sync-change-repository.js";
import { NotFoundError, ServiceError } from "./errors.js";

export class SyncService {
  async pull(userId: string, deviceId: string, cursor: string, limit = 100) {
    if (!/^\d+$/.test(cursor)) throw new ServiceError("cursor must be a non-negative integer", "INVALID_INPUT", 400);
    const device = await new DeviceRepository(db).findByIdForUser(deviceId, userId);
    if (!device) throw new NotFoundError("Device not found");
    const changesRepo = new SyncChangeRepository(db);
    const latest = await changesRepo.latestSequence(userId);
    if (BigInt(cursor) > BigInt(latest)) {
      throw new ServiceError("cursor is ahead of the server change log", "INVALID_INPUT", 400);
    }
    const changes = await changesRepo.listAfter(userId, cursor, limit);
    const nextCursor = changes.length > 0 ? changes[changes.length - 1]!.sequence : cursor;
    return { changes, cursor: nextCursor, hasMore: changes.length === Math.min(Math.max(Math.trunc(limit), 1), 500) };
  }

  async acknowledge(userId: string, deviceId: string, cursor: string): Promise<void> {
    if (!/^\d+$/.test(cursor)) throw new ServiceError("cursor must be a non-negative integer", "INVALID_INPUT", 400);
    const devices = new DeviceRepository(db);
    const device = await devices.findByIdForUser(deviceId, userId);
    if (!device) throw new NotFoundError("Device not found");
    if (BigInt(cursor) < BigInt(device.syncCursor)) throw new ServiceError("cursor cannot move backwards", "INVALID_INPUT", 400);
    const latest = await new SyncChangeRepository(db).latestSequence(userId);
    if (BigInt(cursor) > BigInt(latest)) throw new ServiceError("cursor is ahead of the server change log", "INVALID_INPUT", 400);
    await devices.updateCursor(deviceId, userId, cursor);
  }
}
