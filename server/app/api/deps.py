"""Composition root: the one place where concrete classes are wired together."""

from datetime import timedelta
from functools import lru_cache
from typing import Annotated

from fastapi import Depends
from fastapi.security import OAuth2PasswordBearer
from sqlalchemy.orm import Session

from app.config import settings
from app.core.security import (
    Argon2PasswordHasher,
    JwtTokenService,
    PasswordHasher,
    TokenService,
)
from app.db.models import User
from app.db.session import get_session
from app.repositories.accounts import AccountRepository
from app.repositories.categories import CategoryRepository
from app.repositories.transactions import TransactionRepository
from app.repositories.users import UserRepository
from app.services.accounts import AccountService
from app.services.auth import AuthService
from app.services.categories import CategoryService
from app.services.default_categories import CategorySeeder, DefaultCategorySeeder
from app.services.transactions import TransactionService

oauth2_scheme = OAuth2PasswordBearer(tokenUrl="auth/login")

SessionDep = Annotated[Session, Depends(get_session)]


@lru_cache
def get_hasher() -> PasswordHasher:
    return Argon2PasswordHasher()


@lru_cache
def get_token_service() -> TokenService:
    return JwtTokenService(
        settings.jwt_secret,
        settings.jwt_algorithm,
        timedelta(minutes=settings.access_token_minutes),
    )


def get_category_seeder(session: SessionDep) -> CategorySeeder:
    return DefaultCategorySeeder(CategoryRepository(session))


def get_auth_service(
    session: SessionDep,
    hasher: Annotated[PasswordHasher, Depends(get_hasher)],
    tokens: Annotated[TokenService, Depends(get_token_service)],
    seeder: Annotated[CategorySeeder, Depends(get_category_seeder)],
) -> AuthService:
    return AuthService(UserRepository(session), hasher, tokens, seeder, session)


AuthServiceDep = Annotated[AuthService, Depends(get_auth_service)]


def get_current_user(
    token: Annotated[str, Depends(oauth2_scheme)], auth: AuthServiceDep
) -> User:
    return auth.authenticate(token)


CurrentUser = Annotated[User, Depends(get_current_user)]


def get_account_service(session: SessionDep) -> AccountService:
    return AccountService(AccountRepository(session), TransactionRepository(session), session)


AccountServiceDep = Annotated[AccountService, Depends(get_account_service)]


def get_category_service(session: SessionDep) -> CategoryService:
    return CategoryService(CategoryRepository(session), TransactionRepository(session), session)


CategoryServiceDep = Annotated[CategoryService, Depends(get_category_service)]


def get_transaction_service(session: SessionDep) -> TransactionService:
    return TransactionService(
        TransactionRepository(session),
        AccountRepository(session),
        CategoryRepository(session),
        session,
    )


TransactionServiceDep = Annotated[TransactionService, Depends(get_transaction_service)]
