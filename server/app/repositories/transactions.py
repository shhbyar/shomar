import uuid
from collections.abc import Sequence
from dataclasses import dataclass
from datetime import date, datetime, timezone

from sqlalchemy import select, update
from sqlalchemy.orm import Session

from app.db.models import Transaction


@dataclass(frozen=True)
class TransactionFilters:
    account_id: uuid.UUID | None = None
    category_id: uuid.UUID | None = None
    date_from: date | None = None  # inclusive
    date_to: date | None = None  # inclusive
    updated_since: datetime | None = None  # sync mode, see list_for_user
    limit: int = 100
    offset: int = 0


class TransactionRepository:
    def __init__(self, session: Session) -> None:
        self._session = session

    def list_for_user(self, user_id: uuid.UUID, f: TransactionFilters) -> list[Transaction]:
        stmt = select(Transaction).where(Transaction.user_id == user_id)

        if f.updated_since is not None:
            # Sync mode: includes soft-deleted rows, oldest change first.
            stmt = stmt.where(Transaction.updated_at > f.updated_since).order_by(
                Transaction.updated_at, Transaction.id
            )
        else:
            stmt = stmt.where(Transaction.deleted_at.is_(None)).order_by(
                Transaction.occurred_on.desc(), Transaction.created_at.desc(), Transaction.id
            )

        if f.account_id is not None:
            stmt = stmt.where(Transaction.account_id == f.account_id)
        if f.category_id is not None:
            stmt = stmt.where(Transaction.category_id == f.category_id)
        if f.date_from is not None:
            stmt = stmt.where(Transaction.occurred_on >= f.date_from)
        if f.date_to is not None:
            stmt = stmt.where(Transaction.occurred_on <= f.date_to)

        return list(self._session.scalars(stmt.limit(f.limit).offset(f.offset)))

    def get_for_user(self, user_id: uuid.UUID, transaction_id: uuid.UUID) -> Transaction | None:
        stmt = select(Transaction).where(
            Transaction.id == transaction_id,
            Transaction.user_id == user_id,
            Transaction.deleted_at.is_(None),
        )
        return self._session.scalar(stmt)

    def id_exists(self, transaction_id: uuid.UUID) -> bool:
        return self._session.get(Transaction, transaction_id) is not None

    def add(self, transaction: Transaction) -> None:
        self._session.add(transaction)
        self._session.flush()

    # Bulk updates bump updated_at by hand (the ORM hook doesn't run for them),
    # so syncing clients see the change.

    def soft_delete_for_account(self, user_id: uuid.UUID, account_id: uuid.UUID) -> None:
        now = datetime.now(timezone.utc)
        self._session.execute(
            update(Transaction)
            .where(
                Transaction.user_id == user_id,
                Transaction.account_id == account_id,
                Transaction.deleted_at.is_(None),
            )
            .values(deleted_at=now, updated_at=now)
        )
        
        
    def soft_delete_group(self, user_id: uuid.UUID, group_id: uuid.UUID) -> None:
        """Deletes every leg of a transfer at once."""
        now = datetime.now(timezone.utc)
        self._session.execute(
            update(Transaction)
            .where(
                Transaction.user_id == user_id,
                Transaction.transfer_group_id == group_id,
                Transaction.deleted_at.is_(None),
            )
            .values(deleted_at=now, updated_at=now)
        )

    def clear_category(self, user_id: uuid.UUID, category_ids: Sequence[uuid.UUID]) -> None:
        self._session.execute(
            update(Transaction)
            .where(
                Transaction.user_id == user_id,
                Transaction.category_id.in_(category_ids),
                Transaction.deleted_at.is_(None),
            )
            .values(category_id=None, updated_at=datetime.now(timezone.utc))
        )

    def get_any(self, transaction_id: uuid.UUID) -> Transaction | None:
        """By id only: any owner, including soft-deleted rows (used by PUT)."""
        return self._session.get(Transaction, transaction_id)
