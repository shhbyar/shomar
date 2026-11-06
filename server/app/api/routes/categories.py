import uuid

from fastapi import APIRouter, Response, status

from app.api.deps import CategoryServiceDep, CurrentUser
from app.api.params import UpdatedSince
from app.schemas.categories import CategoryCreate, CategoryRead, CategoryUpdate, CategoryUpsert

router = APIRouter(prefix="/categories", tags=["categories"])


@router.get("", response_model=list[CategoryRead])
def list_categories(
    user: CurrentUser, service: CategoryServiceDep, updated_since: UpdatedSince = None
):
    return service.list(user, updated_since)


@router.post("", response_model=CategoryRead, status_code=status.HTTP_201_CREATED)
def create_category(data: CategoryCreate, user: CurrentUser, service: CategoryServiceDep):
    return service.create(user, data)


@router.get("/{category_id}", response_model=CategoryRead)
def get_category(category_id: uuid.UUID, user: CurrentUser, service: CategoryServiceDep):
    return service.get(user, category_id)


@router.patch("/{category_id}", response_model=CategoryRead)
def update_category(
    category_id: uuid.UUID,
    data: CategoryUpdate,
    user: CurrentUser,
    service: CategoryServiceDep,
):
    return service.update(user, category_id, data)


@router.delete(
    "/{category_id}",
    status_code=status.HTTP_204_NO_CONTENT,
    response_class=Response,
)
def delete_category(category_id: uuid.UUID, user: CurrentUser, service: CategoryServiceDep):
    """Also soft-deletes its subcategories and un-categorizes their transactions."""
    service.delete(user, category_id)


@router.put("/{category_id}", response_model=CategoryRead)
def upsert_category(
    category_id: uuid.UUID,
    data: CategoryUpsert,
    response: Response,
    user: CurrentUser,
    service: CategoryServiceDep,
):
    """Idempotent create-or-replace, safe to retry. 201 when created, 200 when replaced."""
    category, created = service.upsert(user, category_id, data)
    response.status_code = status.HTTP_201_CREATED if created else status.HTTP_200_OK
    return category
