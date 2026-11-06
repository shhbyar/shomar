import uuid
from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field

from app.db.models import AccountType
from app.schemas.common import Currency, MinorUnits


class AccountFields(BaseModel):
    name: str = Field(min_length=1, max_length=100)
    type: AccountType
    currency: Currency
    opening_balance_minor: MinorUnits = 0


class AccountCreate(AccountFields):
    # Clients (e.g. the Android app, offline) may supply their own id.
    id: uuid.UUID | None = None


class AccountUpsert(AccountFields):
    """PUT body: the account's id comes from the URL."""


class AccountUpdate(BaseModel):
    # `currency` is deliberately absent and extra fields are rejected:
    # an account's currency can't change once created.
    model_config = ConfigDict(extra="forbid")

    name: str | None = Field(default=None, min_length=1, max_length=100)
    type: AccountType | None = None
    opening_balance_minor: MinorUnits | None = None


class AccountRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    name: str
    type: AccountType
    currency: str
    opening_balance_minor: int
    created_at: datetime
    updated_at: datetime
    deleted_at: datetime | None
