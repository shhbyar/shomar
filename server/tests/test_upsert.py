import uuid

from tests.conftest import ACCOUNT, make_account, make_category, make_transaction, make_user


def new_id():
    return str(uuid.uuid4())


# ---- accounts ----

def test_account_put_creates_then_replaces_idempotently(client):
    headers = make_user(client)
    account_id = new_id()
    body = {"name": "Main", "type": "checking", "currency": "EUR", "opening_balance_minor": 100}

    first = client.put(f"/accounts/{account_id}", json=body, headers=headers)
    assert first.status_code == 201 and first.json()["id"] == account_id

    retry = client.put(f"/accounts/{account_id}", json=body, headers=headers)  # e.g. the response was lost
    assert retry.status_code == 200 and retry.json()["id"] == account_id
    assert len(client.get("/accounts", headers=headers).json()) == 1

    changed = client.put(f"/accounts/{account_id}", json={**body, "name": "Renamed"}, headers=headers)
    assert changed.status_code == 200 and changed.json()["name"] == "Renamed"


def test_account_put_rules(client):
    headers = make_user(client)
    other = make_user(client, email="other@example.com")
    account = make_account(client, headers)
    body = {"name": "Main", "type": "checking", "currency": "EUR"}

    assert client.put(f"/accounts/{account['id']}", json={**body, "currency": "USD"}, headers=headers).status_code == 422
    assert client.put(f"/accounts/{account['id']}", json=body, headers=other).status_code == 409  # not yours
    client.delete(f"/accounts/{account['id']}", headers=headers)
    assert client.put(f"/accounts/{account['id']}", json=body, headers=headers).status_code == 409  # deleted


# ---- categories ----

def test_category_put_creates_then_replaces_idempotently(client):
    headers = make_user(client)
    food = make_category(client, headers, "Food")
    category_id = new_id()
    body = {"name": "Groceries", "kind": "expense", "parent_id": food["id"]}

    assert client.put(f"/categories/{category_id}", json=body, headers=headers).status_code == 201
    again = client.put(f"/categories/{category_id}", json=body, headers=headers)
    assert again.status_code == 200 and again.json()["parent_id"] == food["id"]

    top_level = client.put(f"/categories/{category_id}", json={**body, "parent_id": None}, headers=headers)
    assert top_level.json()["parent_id"] is None  # full replacement: parent cleared


def test_category_put_rules(client):
    headers = make_user(client)
    other = make_user(client, email="other@example.com")
    food = make_category(client, headers, "Food")
    make_category(client, headers, "Groceries", parent_id=food["id"])
    salary = make_category(client, headers, "Salary", kind="income")

    def put(user_headers, category, **body):
        return client.put(f"/categories/{category['id']}", json={"name": category["name"], "kind": category["kind"], **body}, headers=user_headers).status_code

    assert put(headers, salary, kind="expense") == 422  # kind can't change
    assert put(headers, food, parent_id=salary["id"]) == 422  # has children / kind mismatch
    assert put(other, food) == 409  # not yours


# ---- transactions ----

def test_transaction_put_creates_then_replaces_fully(client):
    headers = make_user(client)
    account = make_account(client, headers)
    food = make_category(client, headers, "Food")
    tx_id = new_id()
    body = {
        "account_id": account["id"], "category_id": food["id"], "amount_minor": -500,
        "occurred_on": "2026-10-01", "merchant": "Shop", "note": "milk",
    }

    assert client.put(f"/transactions/{tx_id}", json=body, headers=headers).status_code == 201
    retry = client.put(f"/transactions/{tx_id}", json=body, headers=headers)
    assert retry.status_code == 200
    assert len(client.get("/transactions", headers=headers).json()) == 1

    minimal = {"account_id": account["id"], "amount_minor": -700, "occurred_on": "2026-10-02"}
    replaced = client.put(f"/transactions/{tx_id}", json=minimal, headers=headers).json()
    assert replaced["amount_minor"] == -700
    assert replaced["merchant"] is None and replaced["note"] is None and replaced["category_id"] is None


def test_transaction_put_rules(client):
    headers = make_user(client)
    other = make_user(client, email="other@example.com")
    account = make_account(client, headers)
    foreign_account = make_account(client, other)
    tx = make_transaction(client, headers, account["id"])
    body = {"account_id": account["id"], "amount_minor": -1, "occurred_on": "2026-10-01"}

    assert client.put(f"/transactions/{tx['id']}", json=body, headers=other).status_code == 409
    assert client.put(f"/transactions/{tx['id']}", json={**body, "account_id": foreign_account["id"]}, headers=headers).status_code == 422
    assert client.put(f"/transactions/{new_id()}", json={**body, "amount_minor": 0}, headers=headers).status_code == 422
    client.delete(f"/transactions/{tx['id']}", headers=headers)
    assert client.put(f"/transactions/{tx['id']}", json=body, headers=headers).status_code == 409
