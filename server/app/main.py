from fastapi import FastAPI

from app.api.errors import register_error_handlers
from app.api.routes import accounts, auth, categories, transactions


def create_app() -> FastAPI:
    app = FastAPI(title="shomar", version="0.1.0")
    register_error_handlers(app)
    app.include_router(auth.router)
    app.include_router(accounts.router)
    app.include_router(categories.router)
    app.include_router(transactions.router)

    @app.get("/health", tags=["meta"])
    def health() -> dict[str, str]:
        return {"status": "ok"}

    return app


app = create_app()
