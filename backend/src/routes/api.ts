import { Router } from "express";
import { requireTelegramBotToken, requireSessionSecret } from "../config.js";
import { AuthService } from "../auth/auth-service.js";
import { requireAuth, type AuthenticatedRequest } from "../auth/middleware.js";
import type { TelegramLoginPayload } from "../auth/types.js";
import { FilesystemService } from "../services/filesystem-service.js";
import { FileService } from "../services/file-service.js";
import { getStorageProvider } from "../storage/provider.js";

const authService = new AuthService();
const filesystemService = new FilesystemService();
const fileService = new FileService(getStorageProvider());

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

  router.get("/storage/capabilities", requireAuth, async (_req: AuthenticatedRequest, res, next) => {
    try {
      res.json({ provider: fileService.storageName, capabilities: await fileService.capabilities() });
    } catch (error) {
      next(error);
    }
  });

  router.get("/storage/health", requireAuth, async (_req: AuthenticatedRequest, res, next) => {
    try {
      res.json(await fileService.healthCheck());
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

  router.post("/files", requireAuth, async (req: AuthenticatedRequest, res, next) => {
    try {
      const folderId = typeof req.query.folderId === "string" ? req.query.folderId : undefined;
      const name = typeof req.query.name === "string" ? req.query.name : undefined;
      const mimeType = req.header("content-type")?.split(";", 1)[0]?.trim() || null;
      const lengthHeader = req.header("content-length");
      const sizeBytes = lengthHeader ? Number(lengthHeader) : NaN;

      if (!folderId || !name || !Number.isSafeInteger(sizeBytes) || sizeBytes < 0) {
        res.status(400).json({ error: { code: "INVALID_INPUT", message: "folderId, name, and a valid Content-Length are required" } });
        return;
      }

      const file = await fileService.upload({
        userId: req.userId!,
        folderId,
        name,
        mimeType,
        sizeBytes,
        body: req,
      });
      res.status(201).json({ file });
    } catch (error) {
      next(error);
    }
  });

  router.get("/files/:fileId/content", requireAuth, async (req: AuthenticatedRequest, res, next) => {
    try {
      const result = await fileService.getDownload(req.userId!, req.params.fileId);
      res.setHeader("Content-Type", result.file.mimeType ?? "application/octet-stream");
      if (result.file.sizeBytes !== null) res.setHeader("Content-Length", result.file.sizeBytes);
      res.setHeader("Content-Disposition", `attachment; filename*=UTF-8''${encodeURIComponent(result.file.name)}`);
      result.body.pipe(res);
    } catch (error) {
      next(error);
    }
  });

  return router;
}
