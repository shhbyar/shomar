from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from app.core.errors import (
    AuthenticationError,
    ConflictError,
    InvalidInputError,
    NotFoundError,
)


def register_error_handlers(app: FastAPI) -> None:
    @app.exception_handler(NotFoundError)
    async def not_found(_: Request, exc: NotFoundError) -> JSONResponse:
        return JSONResponse({"detail": str(exc)}, status_code=404)

    @app.exception_handler(ConflictError)
    async def conflict(_: Request, exc: ConflictError) -> JSONResponse:
        return JSONResponse({"detail": str(exc)}, status_code=409)

    @app.exception_handler(InvalidInputError)
    async def invalid_input(_: Request, exc: InvalidInputError) -> JSONResponse:
        return JSONResponse({"detail": str(exc)}, status_code=422)

    @app.exception_handler(AuthenticationError)
    async def unauthenticated(_: Request, exc: AuthenticationError) -> JSONResponse:
        return JSONResponse(
            {"detail": str(exc)},
            status_code=401,
            headers={"WWW-Authenticate": "Bearer"},
        )
