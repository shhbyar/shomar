import uuid
from datetime import datetime, timezone

from sqlalchemy.orm import Session

from app.core.errors import ConflictError, InvalidInputError, NotFoundError
from app.core.time import as_utc
from app.db.models import Category, CategoryKind, User
from app.repositories.categories import CategoryRepository
from app.repositories.transactions import TransactionRepository
from app.schemas.categories import CategoryCreate, CategoryUpdate, CategoryUpsert


class CategoryService:
    """Categories form at most two levels: a category and its subcategories."""

    def __init__(
        self,
        categories: CategoryRepository,
        transactions: TransactionRepository,
        session: Session,
    ) -> None:
        self._categories = categories
        self._transactions = transactions
        self._session = session

    def list(self, user: User, updated_since: datetime | None = None) -> list[Category]:
        if updated_since is not None:
            updated_since = as_utc(updated_since)
        return self._categories.list_for_user(user.id, updated_since)

    def get(self, user: User, category_id: uuid.UUID) -> Category:
        category = self._categories.get_for_user(user.id, category_id)
        if category is None:
            raise NotFoundError("Category not found")
        return category

    def create(self, user: User, data: CategoryCreate) -> Category:
        if data.parent_id is not None:
            self._check_parent(user, data.parent_id, data.kind)

        fields = dict(
            user_id=user.id, name=data.name, kind=data.kind, parent_id=data.parent_id
        )
        if data.id is not None:
            if self._categories.id_exists(data.id):
                raise ConflictError("A category with this id already exists")
            fields["id"] = data.id

        category = Category(**fields)
        self._categories.add(category)
        self._session.commit()
        self._session.refresh(category)
        return category

    def update(self, user: User, category_id: uuid.UUID, data: CategoryUpdate) -> Category:
        category = self.get(user, category_id)

        if "parent_id" in data.model_fields_set:
            if data.parent_id is not None:
                if self._categories.children_of(user.id, category.id):
                    raise InvalidInputError(
                        "A category with subcategories can't become a subcategory"
                    )
                self._check_parent(user, data.parent_id, category.kind, own_id=category.id)
            category.parent_id = data.parent_id
        if "name" in data.model_fields_set:
            category.name = data.name

        self._session.commit()
        self._session.refresh(category)
        return category

    def delete(self, user: User, category_id: uuid.UUID) -> None:
        """Soft-deletes the category and its subcategories; their transactions
        become uncategorized."""
        category = self.get(user, category_id)
        affected = [category, *self._categories.children_of(user.id, category.id)]
        now = datetime.now(timezone.utc)
        for item in affected:
            item.deleted_at = now
        self._transactions.clear_category(user.id, [item.id for item in affected])
        self._session.commit()

    def _check_parent(
        self,
        user: User,
        parent_id: uuid.UUID,
        kind: CategoryKind,
        own_id: uuid.UUID | None = None,
    ) -> None:
        parent = self._categories.get_for_user(user.id, parent_id)
        if parent is None or parent.id == own_id:
            raise InvalidInputError("Parent category not found")
        if parent.parent_id is not None:
            raise InvalidInputError("Subcategories can't have their own subcategories")
        if parent.kind != kind:
            raise InvalidInputError("A subcategory must be the same kind as its parent")

    def upsert(
        self, user: User, category_id: uuid.UUID, data: CategoryUpsert
    ) -> tuple[Category, bool]:
        """Idempotent create-or-replace. Returns (category, created)."""
        existing = self._categories.get_any(category_id)
        if existing is None:
            created = self.create(user, CategoryCreate(id=category_id, **data.model_dump()))
            return created, True
        if existing.user_id != user.id:
            raise ConflictError("A category with this id already exists")
        if existing.deleted_at is not None:
            raise ConflictError("This category was deleted")
        if data.kind != existing.kind:
            raise InvalidInputError("A category's kind can't change")
        if data.parent_id is not None:
            if self._categories.children_of(user.id, existing.id):
                raise InvalidInputError(
                    "A category with subcategories can't become a subcategory"
                )
            self._check_parent(user, data.parent_id, existing.kind, own_id=existing.id)

        existing.name = data.name
        existing.parent_id = data.parent_id
        self._session.commit()
        self._session.refresh(existing)
        return existing, False
