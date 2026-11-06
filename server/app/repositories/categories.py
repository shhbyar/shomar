import uuid
from datetime import datetime

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.db.models import Category


class CategoryRepository:
    def __init__(self, session: Session) -> None:
        self._session = session

    def list_for_user(
        self, user_id: uuid.UUID, updated_since: datetime | None = None
    ) -> list[Category]:
        stmt = select(Category).where(Category.user_id == user_id)
        if updated_since is None:
            stmt = stmt.where(Category.deleted_at.is_(None)).order_by(
                Category.kind, Category.name
            )
        else:
            stmt = stmt.where(Category.updated_at > updated_since).order_by(
                Category.updated_at, Category.id
            )
        return list(self._session.scalars(stmt))

    def get_for_user(self, user_id: uuid.UUID, category_id: uuid.UUID) -> Category | None:
        stmt = select(Category).where(
            Category.id == category_id,
            Category.user_id == user_id,
            Category.deleted_at.is_(None),
        )
        return self._session.scalar(stmt)

    def children_of(self, user_id: uuid.UUID, parent_id: uuid.UUID) -> list[Category]:
        stmt = select(Category).where(
            Category.user_id == user_id,
            Category.parent_id == parent_id,
            Category.deleted_at.is_(None),
        )
        return list(self._session.scalars(stmt))

    def id_exists(self, category_id: uuid.UUID) -> bool:
        return self._session.get(Category, category_id) is not None

    def add(self, category: Category) -> None:
        self._session.add(category)
        self._session.flush()

    def add_many(self, categories: list[Category]) -> None:
        self._session.add_all(categories)
        self._session.flush()

    def get_any(self, category_id: uuid.UUID) -> Category | None:
        """By id only: any owner, including soft-deleted rows (used by PUT)."""
        return self._session.get(Category, category_id)
