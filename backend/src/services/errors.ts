export class ServiceError extends Error {
  constructor(
    message: string,
    readonly code: string,
    readonly statusCode = 400,
  ) {
    super(message);
    this.name = "ServiceError";
  }
}

export class NotFoundError extends ServiceError {
  constructor(message = "Resource not found") {
    super(message, "NOT_FOUND", 404);
    this.name = "NotFoundError";
  }
}

export class ConflictError extends ServiceError {
  constructor(message = "Resource conflict") {
    super(message, "CONFLICT", 409);
    this.name = "ConflictError";
  }
}
