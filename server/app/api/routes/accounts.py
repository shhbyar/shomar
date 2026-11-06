import uuid

from fastapi import APIRouter, Response, status

from app.api.deps import AccountServiceDep, CurrentUser
from app.api.params import UpdatedSince
from app.schemas.accounts import AccountCreate, AccountRead, AccountUpdate, AccountUpsert

router = APIRouter(prefix="/accounts", tags=["accounts"])


@router.get("", response_model=list[AccountRead])
def list_accounts(
    user: CurrentUser, service: AccountServiceDep, updated_since: UpdatedSince = None
):
    return service.list(user, updated_since)


@router.post("", response_model=AccountRead, status_code=status.HTTP_201_CREATED)
def create_account(data: AccountCreate, user: CurrentUser, service: AccountServiceDep):
    return service.create(user, data)


@router.get("/{account_id}", response_model=AccountRead)
def get_account(account_id: uuid.UUID, user: CurrentUser, service: AccountServiceDep):
    return service.get(user, account_id)


@router.patch("/{account_id}", response_model=AccountRead)
def update_account(
    account_id: uuid.UUID,
    data: AccountUpdate,
    user: CurrentUser,
    service: AccountServiceDep,
):
    return service.update(user, account_id, data)


@router.delete(
    "/{account_id}",
    status_code=status.HTTP_204_NO_CONTENT,
    response_class=Response,
)
def delete_account(account_id: uuid.UUID, user: CurrentUser, service: AccountServiceDep):
    """Also soft-deletes the account's transactions."""
    service.delete(user, account_id)


@router.put("/{account_id}", response_model=AccountRead)
def upsert_account(
    account_id: uuid.UUID,
    data: AccountUpsert,
    response: Response,
    user: CurrentUser,
    service: AccountServiceDep,
):
    """Idempotent create-or-replace, safe to retry. 201 when created, 200 when replaced."""
    account, created = service.upsert(user, account_id, data)
    response.status_code = status.HTTP_201_CREATED if created else status.HTTP_200_OK
    return account
