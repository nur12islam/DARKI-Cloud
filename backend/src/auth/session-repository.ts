import type { DbExecutor } from "../db/repositories/types.js";

export type SessionRecord = {
  id: string;
  userId: string;
  tokenHash: string;
  expiresAt: Date;
  createdAt: Date;
  revokedAt: Date | null;
  lastUsedAt: Date | null;
};

export class SessionRepository {
  constructor(private readonly db: DbExecutor) {}

  async create(input: { id: string; userId: string; tokenHash: string; expiresAt: Date }): Promise<SessionRecord> {
    const result = await this.db.query<SessionRecord>(
      `INSERT INTO sessions (id, user_id, token_hash, expires_at)
       VALUES ($1, $2, $3, $4)
       RETURNING id, user_id AS "userId", token_hash AS "tokenHash", expires_at AS "expiresAt",
                 created_at AS "createdAt", revoked_at AS "revokedAt", last_used_at AS "lastUsedAt"`,
      [input.id, input.userId, input.tokenHash, input.expiresAt],
    );
    return result.rows[0]!;
  }

  async findActiveByTokenHash(tokenHash: string): Promise<SessionRecord | null> {
    const result = await this.db.query<SessionRecord>(
      `UPDATE sessions
          SET last_used_at = now()
        WHERE token_hash = $1 AND revoked_at IS NULL AND expires_at > now()
      RETURNING id, user_id AS "userId", token_hash AS "tokenHash", expires_at AS "expiresAt",
                created_at AS "createdAt", revoked_at AS "revokedAt", last_used_at AS "lastUsedAt"`,
      [tokenHash],
    );
    return result.rows[0] ?? null;
  }

  async cleanupExpired(): Promise<number> {
    const result = await this.db.query(
      `DELETE FROM sessions
        WHERE expires_at <= now() OR revoked_at IS NOT NULL AND revoked_at < now() - INTERVAL '30 days'`,
    );
    return result.rowCount ?? 0;
  }

  async revokeByTokenHash(tokenHash: string): Promise<boolean> {
    const result = await this.db.query(
      `UPDATE sessions SET revoked_at = now()
        WHERE token_hash = $1 AND revoked_at IS NULL
        RETURNING id`,
      [tokenHash],
    );
    return result.rowCount === 1;
  }
}
