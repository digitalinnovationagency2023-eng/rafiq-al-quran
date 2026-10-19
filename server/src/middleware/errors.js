// Central error handling: no stack traces or secrets reach clients.
import { isProduction } from "../config.js";

export class HttpError extends Error {
  constructor(status, message, code) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

export const notFound = (req, res) => res.status(404).json({ error: "Not found" });

export const errorHandler = (error, req, res, next) => {
  void next;
  const status = Number(error.status) || 500;
  if (status >= 500) {
    // Log the detail server-side only.
    console.error(`[error] ${req.method} ${req.originalUrl}:`, error.message);
  }
  res.status(status).json({
    error: status >= 500 && isProduction ? "Something went wrong. Please try again." : error.message,
    code: error.code || undefined,
  });
};

export const asyncHandler = (handler) => (req, res, next) => {
  Promise.resolve(handler(req, res, next)).catch(next);
};
