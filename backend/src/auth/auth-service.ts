import { randomUUID } from "node:crypto";
import { db } from "../db/pool.js";
import { UserRepository } from "../db/repositories/user-repository.js";
import { FolderRepository } from "../db/repositories/folder-repository.js";
import { withTransaction } from "../db/transaction.js";
import { ServiceError } from "../services/errors.js";
import { displayNameFromTelegram, verifyTelegramLogin } from "./telegram-login.js";
import { createSessionToken, hashSessionToken, sessionExpiresAt } from "./session.js";
import { SessionRepository } from "./session-repository.js";
import type { AuthenticatedUser, TelegramLoginPayload } from "./types.js";

export class AuthService {
  async loginWithTelegram(payload: TelegramLoginPayload, botToken: string): Promise<AuthenticatedUser> {
    if (!verifyTelegramLogin(payload, botToken)) {
      throw new ServiceError("Invalid Telegram authentication", "INVALID_AUTH", 401);
    }

    return withTransaction(async (client) => {
      const users = new UserRepository(client);
      const folders = new FolderRepository(client);
      const existing = await users.findByTelegramUserId(payload.id);
      if (existing) return existing;

      const user = await users.create(payload.id, displayNameFromTelegram(payload));
      await folders.create(user.id, null, "My Drive");
      return user;
    });
  }

  async authenticateUser(userId: string): Promise<AuthenticatedUser> {
    const user = await new UserRepository(db).findById(userId);
    if (!user) throw new ServiceError("Authentication required", "UNAUTHENTICATED", 401);
    return user;
  }

  async createSession(userId: string): Promise<string> {
    const token = createSessionToken();
    await new SessionRepository(db).create({
      id: randomUUID(),
      userId,
      tokenHash: hashSessionToken(token),
      expiresAt: sessionExpiresAt(),
    });
    return token;
  }

  async revokeSession(token: string): Promise<void> {
    await new SessionRepository(db).revokeByTokenHash(hashSessionToken(token));
  }
}
