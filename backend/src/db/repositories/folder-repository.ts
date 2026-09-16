import type { DbExecutor, FolderRecord } from "./types.js";

export class FolderRepository {
  constructor(private readonly db: DbExecutor) {}

  async findByIdForUser(id: string, userId: string): Promise<FolderRecord | null> {
    const result = await this.db.query<FolderRecord>(
      `SELECT id, user_id AS "userId", parent_id AS "parentId", name,
              created_at AS "createdAt", modified_at AS "modifiedAt", deleted_at AS "deletedAt"
         FROM folders WHERE id = $1 AND user_id = $2`, [id, userId]);
    return result.rows[0] ?? null;
  }

  async listChildren(userId: string, parentId: string | null): Promise<FolderRecord[]> {
    const result = await this.db.query<FolderRecord>(
      `SELECT id, user_id AS "userId", parent_id AS "parentId", name,
              created_at AS "createdAt", modified_at AS "modifiedAt", deleted_at AS "deletedAt"
         FROM folders
        WHERE user_id = $1 AND parent_id IS NOT DISTINCT FROM $2 AND deleted_at IS NULL
        ORDER BY lower(name), id`, [userId, parentId]);
    return result.rows;
  }

  async create(userId: string, parentId: string | null, name: string): Promise<FolderRecord> {
    const result = await this.db.query<FolderRecord>(
      `INSERT INTO folders (user_id, parent_id, name) VALUES ($1, $2, $3)
       RETURNING id, user_id AS "userId", parent_id AS "parentId", name,
                 created_at AS "createdAt", modified_at AS "modifiedAt", deleted_at AS "deletedAt"`,
      [userId, parentId, name]);
    return result.rows[0]!;
  }

  async rename(id: string, userId: string, name: string): Promise<FolderRecord | null> {
    const result = await this.db.query<FolderRecord>(
      `UPDATE folders SET name = $1, modified_at = now()
        WHERE id = $2 AND user_id = $3 AND deleted_at IS NULL
      RETURNING id, user_id AS "userId", parent_id AS "parentId", name,
                created_at AS "createdAt", modified_at AS "modifiedAt", deleted_at AS "deletedAt"`,
      [name, id, userId]);
    return result.rows[0] ?? null;
  }

  async move(id: string, userId: string, parentId: string): Promise<FolderRecord | null> {
    const result = await this.db.query<FolderRecord>(
      `UPDATE folders SET parent_id = $1, modified_at = now()
        WHERE id = $2 AND user_id = $3 AND deleted_at IS NULL
      RETURNING id, user_id AS "userId", parent_id AS "parentId", name,
                created_at AS "createdAt", modified_at AS "modifiedAt", deleted_at AS "deletedAt"`,
      [parentId, id, userId]);
    return result.rows[0] ?? null;
  }
}
