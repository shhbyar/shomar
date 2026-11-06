import uuid
from dataclasses import replace
from datetime import datetime, timezone

from sqlalchemy.orm import Session

from app.core.errors import ConflictError, InvalidInputError, NotFoundError
from app.core.time import as_utc
from app.db.models import Transaction, User
from app.repositories.accounts import AccountRepository
from app.repositories.categories import CategoryRepository
from app.repositories.transactions import TransactionFilters, TransactionRepository
from app.schemas.transactions import TransactionCreate, TransactionUpdate, TransactionUpsert


class TransactionService:
    def __init__(
        self,
        transactions: TransactionRepository,
        accounts: AccountRepository,
        categories: CategoryRepository,
        session: Session,
    ) -> None:
        self._transactions = transactions
        self._accounts = accounts
        self._categories = categories
        self._session = session

    def list(self, user: User, filters: TransactionFilters) -> list[Transaction]:
        if filters.updated_since is not None:
            filters = replace(filters, updated_since=as_utc(filters.updated_since))
        return self._transactions.list_for_user(user.id, filters)

    def get(self, user: User, transaction_id: uuid.UUID) -> Transaction:
        transaction = self._transactions.get_for_user(user.id, transaction_id)
        if transaction is None:
            raise NotFoundError("Transaction not found")
        return transaction

    def create(self, user: User, data: TransactionCreate) -> Transaction:
        self._check_references(user, data.account_id, data.category_id)
        self._check_transfer(data.transfer_group_id, data.category_id)

        fields = dict(
            # ...the existing fields, then:
            note=data.note,
            transfer_group_id=data.transfer_group_id,
        )

        fields = dict(
            user_id=user.id,
            account_id=data.account_id,
            category_id=data.category_id,
            amount_minor=data.amount_minor,
            occurred_on=data.occurred_on,
            merchant=data.merchant,
            note=data.note,
        )
        if data.id is not None:
            if self._transactions.id_exists(data.id):
                raise ConflictError("A transaction with this id already exists")
            fields["id"] = data.id

        transaction = Transaction(**fields)
        self._transactions.add(transaction)
        self._session.commit()
        self._session.refresh(transaction)
        return transaction

    def update(
        self, user: User, transaction_id: uuid.UUID, data: TransactionUpdate
    ) -> Transaction:
        transaction = self.get(user, transaction_id)
        changes = data.model_dump(exclude_unset=True)
        self._check_references(user, changes.get("account_id"), changes.get("category_id"))
        self._check_transfer(transaction.transfer_group_id, changes.get("category_id"))
        for field, value in changes.items():
            setattr(transaction, field, value)
        self._session.commit()
        self._session.refresh(transaction)
        return transaction

    def delete(self, user: User, transaction_id: uuid.UUID) -> None:
        transaction = self.get(user, transaction_id)
        transaction.deleted_at = datetime.now(timezone.utc)
        self._session.commit()

    def _check_references(
        self,
        user: User,
        account_id: uuid.UUID | None,
        category_id: uuid.UUID | None,
    ) -> None:
        if account_id is not None and self._accounts.get_for_user(user.id, account_id) is None:
            raise InvalidInputError("Account not found")
        if category_id is not None and self._categories.get_for_user(user.id, category_id) is None:
            raise InvalidInputError("Category not found")

    def upsert(
        self, user: User, transaction_id: uuid.UUID, data: TransactionUpsert
    ) -> tuple[Transaction, bool]:
        """Idempotent create-or-replace (a full replacement: omitted optional
        fields are cleared). Returns (transaction, created)."""
        existing = self._transactions.get_any(transaction_id)
        if existing is None:
            created = self.create(user, TransactionCreate(id=transaction_id, **data.model_dump()))
            return created, True
        if existing.user_id != user.id:
            raise ConflictError("A transaction with this id already exists")
        if existing.deleted_at is not None:
            raise ConflictError("This transaction was deleted")
        self._check_references(user, data.account_id, data.category_id)

        for field, value in data.model_dump().items():
            setattr(existing, field, value)
        self._session.commit()
        self._session.refresh(existing)
        return existing, False
