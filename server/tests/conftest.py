import os
from contextlib import contextmanager

# Must be set before the app is imported (settings are read at import time).
os.environ.setdefault("DATABASE_URL", "sqlite://")
os.environ.setdefault("JWT_SECRET", "test-secret-0123456789-0123456789-abcdef")

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

from app.api.deps import get_category_seeder
from app.db.models import Base
from app.db.session import get_session
from app.main import create_app

ACCOUNT = {"name": "Main", "type": "checking", "currency": "EUR", "opening_balance_minor": 150000}


class NoCategories:
    """Stand-in seeder: most tests want to start with an empty category list."""

    def seed(self, user_id):
        pass


@contextmanager
def build_client(seeder_override=None):
    """App wired to a throwaway in-memory SQLite database.

    With no override the real default categories are seeded at registration.
    """
    engine = create_engine(
        "sqlite://",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    Base.metadata.create_all(engine)
    testing_session = sessionmaker(bind=engine, expire_on_commit=False)

    def override_get_session():
        with testing_session() as session:
            yield session

    app = create_app()
    app.dependency_overrides[get_session] = override_get_session
    if seeder_override is not None:
        app.dependency_overrides[get_category_seeder] = seeder_override
    with TestClient(app) as test_client:
        yield test_client


@pytest.fixture
def client():
    """No default categories."""
    with build_client(seeder_override=NoCategories) as test_client:
        yield test_client


@pytest.fixture
def seeded_client():
    """Real behavior: new users get the default categories."""
    with build_client() as test_client:
        yield test_client


def make_user(client, email="a@example.com", password="correct-horse-battery"):
    """Register + log in; returns Authorization headers."""
    r = client.post(
        "/auth/register",
        json={"email": email, "password": password, "default_currency": "EUR"},
    )
    assert r.status_code == 201, r.text
    r = client.post("/auth/login", data={"username": email, "password": password})
    assert r.status_code == 200, r.text
    return {"Authorization": f"Bearer {r.json()['access_token']}"}


def make_account(client, headers, **overrides):
    r = client.post("/accounts", json={**ACCOUNT, **overrides}, headers=headers)
    assert r.status_code == 201, r.text
    return r.json()


def make_category(client, headers, name="Food", kind="expense", **extra):
    r = client.post("/categories", json={"name": name, "kind": kind, **extra}, headers=headers)
    assert r.status_code == 201, r.text
    return r.json()


def make_transaction(client, headers, account_id, amount=-1250, day="2026-10-01", **extra):
    r = client.post(
        "/transactions",
        json={"account_id": account_id, "amount_minor": amount, "occurred_on": day, **extra},
        headers=headers,
    )
    assert r.status_code == 201, r.text
    return r.json()
