import { db } from "../db/pool.js";
import { DeviceRepository } from "../db/repositories/device-repository.js";
import { SyncChangeRepository } from "../db/repositories/sync-change-repository.js";
import { NotFoundError, ServiceError } from "./errors.js";

export class SyncService {
  async pull(userId: string, deviceId: string, cursor: string, limit = 100) {
    if (!/^\d+$/.test(cursor)) throw new ServiceError("cursor must be a non-negative integer", "INVALID_INPUT", 400);
    const devices = new DeviceRepository(db);
    const device = await devices.findByIdForUser(deviceId, userId);
    if (!device) throw new NotFoundError("Device not found");

    const changes = await new SyncChangeRepository(db).listAfter(userId, cursor, limit);
    const nextCursor = changes.length > 0 ? changes[changes.length - 1]!.sequence : cursor;
    await devices.updateCursor(deviceId, userId, nextCursor);
    return { changes, cursor: nextCursor, hasMore: changes.length === Math.min(Math.max(Math.trunc(limit), 1), 500) };
  }
}
