"""Starter categories every new user receives.

They are copied into the user's own rows at registration, so from then on they
are ordinary categories: renamable, deletable, and synced like any other.
Editing this list only affects users who register afterwards.
"""

import uuid
from collections.abc import Sequence
from dataclasses import dataclass
from typing import Protocol

from app.db.models import Category, CategoryKind
from app.repositories.categories import CategoryRepository


@dataclass(frozen=True)
class DefaultCategory:
    name: str
    kind: CategoryKind
    children: tuple[str, ...] = ()  # subcategories, same kind as the parent


_EXPENSE = CategoryKind.EXPENSE
_INCOME = CategoryKind.INCOME

DEFAULT_CATEGORIES: tuple[DefaultCategory, ...] = (
    DefaultCategory("Groceries", _EXPENSE),
    DefaultCategory("Dining out", _EXPENSE),
    DefaultCategory("Rent", _EXPENSE),
    DefaultCategory("Utilities", _EXPENSE),
    DefaultCategory("Transport", _EXPENSE),
    DefaultCategory("Health", _EXPENSE),
    DefaultCategory("Clothes", _EXPENSE),
    DefaultCategory("Entertainment", _EXPENSE),  # e.g. children=("Movies", "Concerts")
    DefaultCategory("Education", _EXPENSE),
    DefaultCategory("Travel", _EXPENSE),
    DefaultCategory("Gifts", _EXPENSE),
    DefaultCategory("Personal care", _EXPENSE),
    DefaultCategory("Other", _EXPENSE),
    DefaultCategory("Salary", _INCOME),
    DefaultCategory("Other income", _INCOME),
)


class CategorySeeder(Protocol):
    def seed(self, user_id: uuid.UUID) -> None: ...


class DefaultCategorySeeder:
    """Adds the default categories to the current transaction (the caller commits)."""

    def __init__(
        self,
        categories: CategoryRepository,
        defaults: Sequence[DefaultCategory] = DEFAULT_CATEGORIES,
    ) -> None:
        self._categories = categories
        self._defaults = defaults

    def seed(self, user_id: uuid.UUID) -> None:
        rows: list[Category] = []
        for default in self._defaults:
            parent = Category(
                id=uuid.uuid4(), user_id=user_id, name=default.name, kind=default.kind
            )
            rows.append(parent)
            for child_name in default.children:
                rows.append(
                    Category(
                        id=uuid.uuid4(),
                        user_id=user_id,
                        parent_id=parent.id,
                        name=child_name,
                        kind=default.kind,
                    )
                )
        self._categories.add_many(rows)  # parents come before their children
