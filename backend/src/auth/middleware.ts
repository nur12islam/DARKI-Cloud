import type { NextFunction, Request, Response } from "express";
import { SessionRepository } from "./session-repository.js";
import { hashSessionToken } from "./session.js";
import { db } from "../db/pool.js";

export type AuthenticatedRequest = Request & { userId?: string; sessionToken?: string };

export async function requireAuth(req: AuthenticatedRequest, res: Response, next: NextFunction): Promise<void> {
  const header = req.header("authorization");
  const token = header?.startsWith("Bearer ") ? header.slice(7).trim() : "";
  if (!token || token.length < 20) {
    res.status(401).json({ error: { code: "UNAUTHENTICATED", message: "Authentication required" } });
    return;
  }

  try {
    const session = await new SessionRepository(db).findActiveByTokenHash(hashSessionToken(token));
    if (!session) {
      res.status(401).json({ error: { code: "UNAUTHENTICATED", message: "Authentication required" } });
      return;
    }
    req.userId = session.userId;
    req.sessionToken = token;
    next();
  } catch (error) {
    next(error);
  }
}
