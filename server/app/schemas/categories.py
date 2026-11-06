import uuid
from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field, model_validator

from app.db.models import CategoryKind


class CategoryFields(BaseModel):
    name: str = Field(min_length=1, max_length=100)
    kind: CategoryKind
    parent_id: uuid.UUID | None = None


class CategoryCreate(CategoryFields):
    id: uuid.UUID | None = None


class CategoryUpsert(CategoryFields):
    """PUT body: the category's id comes from the URL."""


class CategoryUpdate(BaseModel):
    # `kind` can't change after creation (extra fields are rejected).
    model_config = ConfigDict(extra="forbid")

    name: str | None = Field(default=None, min_length=1, max_length=100)
    parent_id: uuid.UUID | None = None  # send null to make it a top-level category

    @model_validator(mode="after")
    def _name_is_not_null(self):
        if "name" in self.model_fields_set and self.name is None:
            raise ValueError("name cannot be null")
        return self


class CategoryRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    name: str
    kind: CategoryKind
    parent_id: uuid.UUID | None
    created_at: datetime
    updated_at: datetime
    deleted_at: datetime | None
