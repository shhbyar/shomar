import uuid
from datetime import date, datetime

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator

from app.schemas.common import MinorUnits


def _nonzero(value: int | None) -> int | None:
    if value == 0:
        raise ValueError("amount_minor cannot be zero")
    return value


class TransactionFields(BaseModel):
    account_id: uuid.UUID
    category_id: uuid.UUID | None = None
    amount_minor: MinorUnits  # negative = money out, positive = money in
    occurred_on: date
    merchant: str | None = Field(default=None, max_length=200)
    note: str | None = Field(default=None, max_length=2000)
    
    # Set on both legs of a transfer between two accounts (the same id on each).
    transfer_group_id: uuid.UUID | None = None

    _check_amount = field_validator("amount_minor")(_nonzero)


class TransactionCreate(TransactionFields):
    id: uuid.UUID | None = None


class TransactionUpsert(TransactionFields):
    """PUT body: the transaction's id comes from the URL."""


class TransactionUpdate(BaseModel):
    model_config = ConfigDict(extra="forbid")

    account_id: uuid.UUID | None = None
    category_id: uuid.UUID | None = None  # send null to un-categorize
    amount_minor: MinorUnits | None = None
    occurred_on: date | None = None
    merchant: str | None = Field(default=None, max_length=200)  # null clears it
    note: str | None = Field(default=None, max_length=2000)  # null clears it

    _check_amount = field_validator("amount_minor")(_nonzero)

    @model_validator(mode="after")
    def _required_fields_are_not_null(self):
        for name in ("account_id", "amount_minor", "occurred_on"):
            if name in self.model_fields_set and getattr(self, name) is None:
                raise ValueError(f"{name} cannot be null")
        return self


class TransactionRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    account_id: uuid.UUID
    category_id: uuid.UUID | None
    amount_minor: int
    occurred_on: date
    merchant: str | None
    note: str | None
    transfer_group_id: uuid.UUID | None
    created_at: datetime
    updated_at: datetime
    deleted_at: datetime | None
