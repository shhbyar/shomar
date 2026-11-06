import uuid
from datetime import date
from typing import Annotated

from fastapi import APIRouter, Query, Response, status

from app.api.deps import CurrentUser, TransactionServiceDep
from app.api.params import UpdatedSince
from app.repositories.transactions import TransactionFilters
from app.schemas.transactions import (
    TransactionCreate,
    TransactionRead,
    TransactionUpdate,
    TransactionUpsert,
)

router = APIRouter(prefix="/transactions", tags=["transactions"])


@router.get("", response_model=list[TransactionRead])
def list_transactions(
    user: CurrentUser,
    service: TransactionServiceDep,
    account_id: uuid.UUID | None = None,
    category_id: uuid.UUID | None = None,
    date_from: date | None = None,
    date_to: date | None = None,
    updated_since: UpdatedSince = None,
    limit: Annotated[int, Query(ge=1, le=1000)] = 100,
    offset: Annotated[int, Query(ge=0)] = 0,
):
    """Newest first. With `updated_since`, oldest change first and deleted rows included."""
    filters = TransactionFilters(
        account_id=account_id,
        category_id=category_id,
        date_from=date_from,
        date_to=date_to,
        updated_since=updated_since,
        limit=limit,
        offset=offset,
    )
    return service.list(user, filters)


@router.post("", response_model=TransactionRead, status_code=status.HTTP_201_CREATED)
def create_transaction(
    data: TransactionCreate, user: CurrentUser, service: TransactionServiceDep
):
    return service.create(user, data)


@router.get("/{transaction_id}", response_model=TransactionRead)
def get_transaction(
    transaction_id: uuid.UUID, user: CurrentUser, service: TransactionServiceDep
):
    return service.get(user, transaction_id)


@router.patch("/{transaction_id}", response_model=TransactionRead)
def update_transaction(
    transaction_id: uuid.UUID,
    data: TransactionUpdate,
    user: CurrentUser,
    service: TransactionServiceDep,
):
    return service.update(user, transaction_id, data)


@router.delete(
    "/{transaction_id}",
    status_code=status.HTTP_204_NO_CONTENT,
    response_class=Response,
)
def delete_transaction(
    transaction_id: uuid.UUID, user: CurrentUser, service: TransactionServiceDep
):
    service.delete(user, transaction_id)


@router.put("/{transaction_id}", response_model=TransactionRead)
def upsert_transaction(
    transaction_id: uuid.UUID,
    data: TransactionUpsert,
    response: Response,
    user: CurrentUser,
    service: TransactionServiceDep,
):
    """Idempotent create-or-replace, safe to retry. 201 when created, 200 when replaced."""
    transaction, created = service.upsert(user, transaction_id, data)
    response.status_code = status.HTTP_201_CREATED if created else status.HTTP_200_OK
    return transaction
