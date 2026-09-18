import { closeDatabase, db } from "../db/pool.js";
import { SessionRepository } from "../auth/session-repository.js";

try {
  const sessions = await new SessionRepository(db).cleanupExpired();
  const attempts = await db.query(
    `DELETE FROM telegram_login_attempts
      WHERE expires_at <= now()
         OR used_at IS NOT NULL AND used_at < now() - INTERVAL '1 day'`,
  );
  console.log(JSON.stringify({ sessions, loginAttempts: attempts.rowCount ?? 0 }));
} finally {
  await closeDatabase();
}
