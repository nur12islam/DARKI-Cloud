import { Router } from "express";
import { requireTelegramBotToken, requireSessionSecret } from "../config.js";
import { AuthService } from "../auth/auth-service.js";
import { requireAuth, type AuthenticatedRequest } from "../auth/middleware.js";
import type { TelegramLoginPayload } from "../auth/types.js";
import { FilesystemService } from "../services/filesystem-service.js";

const authService = new AuthService();
const filesystemService = new FilesystemService();

export function createApiRouter(): Router {
  const router = Router();

  router.post("/auth/telegram", async (req, res, next) => {
    try {
      const payload = req.body as TelegramLoginPayload;
      const user = await authService.loginWithTelegram(payload, requireTelegramBotToken());
      const token = authService.issueSessionToken(user.id, requireSessionSecret());
      res.status(200).json({ user, token, tokenType: "Bearer" });
    } catch (error) {
      next(error);
    }
  });

  router.get("/me", requireAuth, async (req: AuthenticatedRequest, res, next) => {
    try {
      const user = await authService.authenticateUser(req.userId!);
      res.json({ user });
    } catch (error) {
      next(error);
    }
  });

  router.post("/devices", requireAuth, async (req: AuthenticatedRequest, res, next) => {
    try {
      const { deviceName, platform } = req.body as { deviceName?: string; platform?: string };
      if (!deviceName?.trim() || !platform?.trim()) {
        res.status(400).json({ error: { code: "INVALID_INPUT", message: "deviceName and platform are required" } });
        return;
      }
      const device = await filesystemService.registerDevice(req.userId!, deviceName, platform);
      res.status(201).json({ device });
    } catch (error) {
      next(error);
    }
  });

  router.get("/folders/:folderId", requireAuth, async (req: AuthenticatedRequest, res, next) => {
    try {
      const result = await filesystemService.listFolder(req.userId!, req.params.folderId);
      res.json(result);
    } catch (error) {
      next(error);
    }
  });

  router.post("/folders", requireAuth, async (req: AuthenticatedRequest, res, next) => {
    try {
      const { parentId, name, deviceId } = req.body as {
        parentId?: string;
        name?: string;
        deviceId?: string;
      };
      if (!parentId || !name) {
        res.status(400).json({ error: { code: "INVALID_INPUT", message: "parentId and name are required" } });
        return;
      }
      const folder = await filesystemService.createFolder(req.userId!, parentId, name, deviceId);
      res.status(201).json({ folder });
    } catch (error) {
      next(error);
    }
  });

  return router;
}
