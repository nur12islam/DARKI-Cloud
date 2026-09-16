import type { DbExecutor, SyncChangeRecord } from "./types.js";

export class SyncChangeRepository {
  constructor(private readonly db: DbExecutor) {}

  async append(input: {
    userId: string;
    deviceId?: string | null;
    operationId: string;
    entityType: string;
    entityId: string;
    operation: string;
    payload?: Record<string, unknown>;
  }): Promise<SyncChangeRecord> {
    const result = await this.db.query<SyncChangeRecord>(
      `INSERT INTO sync_changes
         (user_id, device_id, operation_id, entity_type, entity_id, operation, payload)
       VALUES ($1, $2, $3, $4, $5, $6, $7::jsonb)
       RETURNING sequence, user_id AS "userId", device_id AS "deviceId",
                 operation_id AS "operationId", entity_type AS "entityType",
                 entity_id AS "entityId", operation, payload,
                 created_at AS "createdAt"`,
      [input.userId, input.deviceId ?? null, input.operationId, input.entityType,
        input.entityId, input.operation, JSON.stringify(input.payload ?? {})],
    );
    return result.rows[0]!;
  }

  async listAfter(userId: string, cursor: string, limit = 100): Promise<SyncChangeRecord[]> {
    const safeLimit = Math.min(Math.max(Math.trunc(limit), 1), 500);
    const result = await this.db.query<SyncChangeRecord>(
      `SELECT sequence, user_id AS "userId", device_id AS "deviceId",
              operation_id AS "operationId", entity_type AS "entityType",
              entity_id AS "entityId", operation, payload,
              created_at AS "createdAt"
         FROM sync_changes
        WHERE user_id = $1 AND sequence > $2
        ORDER BY sequence ASC
        LIMIT ${safeLimit}`,
      [userId, cursor],
    );
    return result.rows;
  }
}
