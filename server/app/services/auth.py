from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.core.errors import AuthenticationError, ConflictError
from app.core.security import PasswordHasher, TokenService
from app.db.models import User
from app.repositories.users import UserRepository
from app.services.default_categories import CategorySeeder
from app.schemas.auth import RegisterRequest


def _normalize_email(email: str) -> str:
    return email.strip().lower()


class AuthService:
    def __init__(
        self,
        users: UserRepository,
        hasher: PasswordHasher,
        tokens: TokenService,
        category_seeder: CategorySeeder,
        session: Session,
    ) -> None:
        self._users = users
        self._hasher = hasher
        self._tokens = tokens
        self._category_seeder = category_seeder
        self._session = session

    def register(self, data: RegisterRequest) -> User:
        email = _normalize_email(data.email)
        if self._users.get_by_email(email) is not None:
            raise ConflictError("Email already registered")

        user = User(
            email=email,
            password_hash=self._hasher.hash(data.password),
            display_name=data.display_name,
            default_currency=data.default_currency,
        )
        try:
            self._users.add(user)
            self._category_seeder.seed(user.id)  # same transaction as the user
            self._session.commit()
        except IntegrityError as exc:  # two registrations racing
            self._session.rollback()
            raise ConflictError("Email already registered") from exc
        self._session.refresh(user)
        return user

    def login(self, email: str, password: str) -> str:
        user = self._users.get_by_email(_normalize_email(email))
        password_ok = self._hasher.verify(password, user.password_hash if user else None)
        if user is None or not password_ok:
            raise AuthenticationError("Incorrect email or password")
        return self._tokens.issue(user.id)

    def authenticate(self, token: str) -> User:
        user = self._users.get(self._tokens.verify(token))
        if user is None:
            raise AuthenticationError("Invalid or expired token")
        return user
