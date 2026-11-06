import uuid
from datetime import datetime, timezone

from sqlalchemy.orm import Session

from app.core.errors import ConflictError, InvalidInputError, NotFoundError
from app.core.time import as_utc
from app.db.models import Account, User
from app.repositories.accounts import AccountRepository
from app.repositories.transactions import TransactionRepository
from app.schemas.accounts import AccountCreate, AccountUpdate, AccountUpsert


class AccountService:
    def __init__(
        self,
        accounts: AccountRepository,
        transactions: TransactionRepository,
        session: Session,
    ) -> None:
        self._accounts = accounts
        self._transactions = transactions
        self._session = session

    def list(self, user: User, updated_since: datetime | None = None) -> list[Account]:
        if updated_since is not None:
            updated_since = as_utc(updated_since)
        return self._accounts.list_for_user(user.id, updated_since)

    def get(self, user: User, account_id: uuid.UUID) -> Account:
        account = self._accounts.get_for_user(user.id, account_id)
        if account is None:
            raise NotFoundError("Account not found")
        return account

    def create(self, user: User, data: AccountCreate) -> Account:
        fields = dict(
            user_id=user.id,
            name=data.name,
            type=data.type,
            currency=data.currency,
            opening_balance_minor=data.opening_balance_minor,
        )
        if data.id is not None:
            if self._accounts.id_exists(data.id):
                raise ConflictError("An account with this id already exists")
            fields["id"] = data.id

        account = Account(**fields)
        self._accounts.add(account)
        self._session.commit()
        self._session.refresh(account)
        return account

    def update(self, user: User, account_id: uuid.UUID, data: AccountUpdate) -> Account:
        account = self.get(user, account_id)
        changes = {k: v for k, v in data.model_dump(exclude_unset=True).items() if v is not None}
        for field, value in changes.items():
            setattr(account, field, value)
        self._session.commit()
        self._session.refresh(account)
        return account

    def delete(self, user: User, account_id: uuid.UUID) -> None:
        """Soft delete the account and its transactions; clients learn about it
        through `deleted_at` when syncing."""
        account = self.get(user, account_id)
        account.deleted_at = datetime.now(timezone.utc)
        self._transactions.soft_delete_for_account(user.id, account.id)
        self._session.commit()

    def upsert(
        self, user: User, account_id: uuid.UUID, data: AccountUpsert
    ) -> tuple[Account, bool]:
        """Idempotent create-or-replace. Returns (account, created)."""
        existing = self._accounts.get_any(account_id)
        if existing is None:
            created = self.create(user, AccountCreate(id=account_id, **data.model_dump()))
            return created, True
        if existing.user_id != user.id:
            raise ConflictError("An account with this id already exists")
        if existing.deleted_at is not None:
            raise ConflictError("This account was deleted")
        if data.currency != existing.currency:
            raise InvalidInputError("An account's currency can't change")

        existing.name = data.name
        existing.type = data.type
        existing.opening_balance_minor = data.opening_balance_minor
        self._session.commit()
        self._session.refresh(existing)
        return existing, False
