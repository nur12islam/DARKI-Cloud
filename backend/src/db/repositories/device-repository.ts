import type { DbExecutor, DeviceRecord } from "./types.js";

export class DeviceRepository {
  constructor(private readonly db: DbExecutor) {}

  async findByIdForUser(id: string, userId: string): Promise<DeviceRecord | null> {
    const result = await this.db.query<DeviceRecord>(
      `SELECT id, user_id AS "userId", device_name AS "deviceName", platform,
              last_seen_at AS "lastSeenAt", sync_cursor AS "syncCursor", created_at AS "createdAt"
         FROM devices
        WHERE id = $1 AND user_id = $2`,
      [id, userId],
    );
    return result.rows[0] ?? null;
  }

  async create(userId: string, deviceName: string, platform: string): Promise<DeviceRecord> {
    const result = await this.db.query<DeviceRecord>(
      `INSERT INTO devices (user_id, device_name, platform, last_seen_at)
       VALUES ($1, $2, $3, now())
       RETURNING id, user_id AS "userId", device_name AS "deviceName", platform,
                 last_seen_at AS "lastSeenAt", sync_cursor AS "syncCursor", created_at AS "createdAt"`,
      [userId, deviceName, platform],
    );
    return result.rows[0]!;
  }

  async updateCursor(id: string, userId: string, cursor: string): Promise<void> {
    await this.db.query(
      `UPDATE devices
          SET sync_cursor = $1, last_seen_at = now()
        WHERE id = $2 AND user_id = $3`,
      [cursor, id, userId],
    );
  }
}
