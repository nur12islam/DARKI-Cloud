import type { DbExecutor, UserRecord } from "./types.js";

export class UserRepository {
  constructor(private readonly db: DbExecutor) {}

  async findById(id: string): Promise<UserRecord | null> {
    const result = await this.db.query<UserRecord>(
      `SELECT id,
              telegram_user_id AS "telegramUserId",
              display_name AS "displayName",
              created_at AS "createdAt"
         FROM users
        WHERE id = $1`,
      [id],
    );
    return result.rows[0] ?? null;
  }

  async findByTelegramUserId(telegramUserId: string): Promise<UserRecord | null> {
    const result = await this.db.query<UserRecord>(
      `SELECT id,
              telegram_user_id AS "telegramUserId",
              display_name AS "displayName",
              created_at AS "createdAt"
         FROM users
        WHERE telegram_user_id = $1`,
      [telegramUserId],
    );
    return result.rows[0] ?? null;
  }

  async create(telegramUserId: string, displayName: string): Promise<UserRecord> {
    const result = await this.db.query<UserRecord>(
      `INSERT INTO users (telegram_user_id, display_name)
       VALUES ($1, $2)
       RETURNING id,
                 telegram_user_id AS "telegramUserId",
                 display_name AS "displayName",
                 created_at AS "createdAt"`,
      [telegramUserId, displayName],
    );
    return result.rows[0]!;
  }
}
