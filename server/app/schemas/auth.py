import uuid

from pydantic import BaseModel, ConfigDict, EmailStr, Field


class RegisterRequest(BaseModel):
    email: EmailStr
    password: str = Field(min_length=8, max_length=128)
    display_name: str | None = Field(default=None, max_length=100)
    default_currency: str = Field(pattern=r"^[A-Z]{3}$")  # ISO 4217


class UserRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    email: str
    display_name: str | None
    default_currency: str


class TokenResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
