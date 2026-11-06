import uuid
from datetime import datetime, timedelta, timezone
from typing import Protocol

import jwt
from pwdlib import PasswordHash

from app.core.errors import AuthenticationError


class PasswordHasher(Protocol):
    def hash(self, password: str) -> str: ...

    def verify(self, password: str, password_hash: str | None) -> bool: ...


class TokenService(Protocol):
    def issue(self, user_id: uuid.UUID) -> str: ...

    def verify(self, token: str) -> uuid.UUID: ...


class Argon2PasswordHasher:
    def __init__(self) -> None:
        self._hasher = PasswordHash.recommended()
        # Verified against when the user doesn't exist, so that login timing
        # doesn't reveal which emails are registered.
        self._dummy_hash = self._hasher.hash("shomar-dummy-password")

    def hash(self, password: str) -> str:
        return self._hasher.hash(password)

    def verify(self, password: str, password_hash: str | None) -> bool:
        valid = self._hasher.verify(password, password_hash or self._dummy_hash)
        return valid and password_hash is not None


class JwtTokenService:
    def __init__(self, secret: str, algorithm: str, ttl: timedelta) -> None:
        self._secret = secret
        self._algorithm = algorithm
        self._ttl = ttl

    def issue(self, user_id: uuid.UUID) -> str:
        now = datetime.now(timezone.utc)
        payload = {"sub": str(user_id), "iat": now, "exp": now + self._ttl, "typ": "access"}
        return jwt.encode(payload, self._secret, algorithm=self._algorithm)

    def verify(self, token: str) -> uuid.UUID:
        try:
            payload = jwt.decode(
                token,
                self._secret,
                algorithms=[self._algorithm],
                options={"require": ["exp", "sub"]},
            )
            if payload.get("typ") != "access":
                raise AuthenticationError("Invalid or expired token")
            return uuid.UUID(payload["sub"])
        except (jwt.InvalidTokenError, ValueError) as exc:
            raise AuthenticationError("Invalid or expired token") from exc
