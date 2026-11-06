from typing import Annotated

from fastapi import APIRouter, Depends, status
from fastapi.security import OAuth2PasswordRequestForm

from app.api.deps import AuthServiceDep, CurrentUser
from app.schemas.auth import RegisterRequest, TokenResponse, UserRead

router = APIRouter(prefix="/auth", tags=["auth"])


@router.post("/register", response_model=UserRead, status_code=status.HTTP_201_CREATED)
def register(data: RegisterRequest, auth: AuthServiceDep):
    return auth.register(data)


@router.post("/login", response_model=TokenResponse)
def login(form: Annotated[OAuth2PasswordRequestForm, Depends()], auth: AuthServiceDep):
    # OAuth2 forms call the field "username"; here it holds the email.
    return TokenResponse(access_token=auth.login(form.username, form.password))


@router.get("/me", response_model=UserRead)
def me(user: CurrentUser):
    return user
