import type { NextFunction, Request, Response } from "express";
import { requireSessionSecret } from "../config.js";
import { verifySessionToken } from "./session.js";

export type AuthenticatedRequest = Request & { userId?: string };

export function requireAuth(req: AuthenticatedRequest, res: Response, next: NextFunction): void {
  const header = req.header("authorization");
  const token = header?.startsWith("Bearer ") ? header.slice(7).trim() : "";
  const userId = token ? verifySessionToken(token, requireSessionSecret()) : null;

  if (!userId) {
    res.status(401).json({ error: { code: "UNAUTHENTICATED", message: "Authentication required" } });
    return;
  }

  req.userId = userId;
  next();
}
