import uuid
from datetime import datetime

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.db.models import Account


class AccountRepository:
    """Data access only. Every read is scoped to the owning user."""

    def __init__(self, session: Session) -> None:
        self._session = session

    def list_for_user(
        self, user_id: uuid.UUID, updated_since: datetime | None = None
    ) -> list[Account]:
        stmt = select(Account).where(Account.user_id == user_id)
        if updated_since is None:
            stmt = stmt.where(Account.deleted_at.is_(None)).order_by(
                Account.created_at, Account.name
            )
        else:  # sync mode: changes (including deletions) in the order they happened
            stmt = stmt.where(Account.updated_at > updated_since).order_by(
                Account.updated_at, Account.id
            )
        return list(self._session.scalars(stmt))

    def get_for_user(self, user_id: uuid.UUID, account_id: uuid.UUID) -> Account | None:
        stmt = select(Account).where(
            Account.id == account_id,
            Account.user_id == user_id,
            Account.deleted_at.is_(None),
        )
        return self._session.scalar(stmt)

    def id_exists(self, account_id: uuid.UUID) -> bool:
        """True for any owner, including soft-deleted rows (primary keys are global)."""
        return self._session.get(Account, account_id) is not None

    def add(self, account: Account) -> None:
        self._session.add(account)
        self._session.flush()

    def get_any(self, account_id: uuid.UUID) -> Account | None:
        """By id only: any owner, including soft-deleted rows (used by PUT)."""
        return self._session.get(Account, account_id)
