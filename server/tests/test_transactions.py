import uuid

from tests.conftest import make_account, make_category, make_transaction, make_user

EVERYTHING = {"updated_since": "2000-01-01T00:00:00Z"}


def test_create_get_and_list_newest_first(client):
    headers = make_user(client)
    account = make_account(client, headers)
    old = make_transaction(client, headers, account["id"], -500, "2026-09-01", merchant="Bakery", note="bread")
    new = make_transaction(client, headers, account["id"], 250000, "2026-10-05", merchant="Employer")

    assert old["merchant"] == "Bakery" and old["deleted_at"] is None
    assert client.get(f"/transactions/{old['id']}", headers=headers).json()["note"] == "bread"
    assert [t["id"] for t in client.get("/transactions", headers=headers).json()] == [new["id"], old["id"]]


def test_filters_and_paging(client):
    headers = make_user(client)
    main = make_account(client, headers, name="Main")
    cash = make_account(client, headers, name="Cash", type="cash")
    food = make_category(client, headers, "Food")
    a = make_transaction(client, headers, main["id"], -100, "2026-09-10", category_id=food["id"])
    b = make_transaction(client, headers, main["id"], -200, "2026-09-20")
    c = make_transaction(client, headers, cash["id"], -300, "2026-10-01", category_id=food["id"])

    def ids(**params):
        return [t["id"] for t in client.get("/transactions", params=params, headers=headers).json()]

    assert ids(account_id=cash["id"]) == [c["id"]]
    assert ids(category_id=food["id"]) == [c["id"], a["id"]]
    assert ids(date_from="2026-09-20", date_to="2026-09-20") == [b["id"]]  # both ends inclusive
    assert ids(date_from="2026-09-15") == [c["id"], b["id"]]
    assert ids(limit=1) == [c["id"]]
    assert ids(limit=1, offset=1) == [b["id"]]
    assert client.get("/transactions", params={"limit": 0}, headers=headers).status_code == 422


def test_references_and_amounts_are_validated(client):
    headers = make_user(client)
    other = make_user(client, email="other@example.com")
    account = make_account(client, headers)
    foreign_account = make_account(client, other)
    foreign_category = make_category(client, other)

    def create(**overrides):
        body = {"account_id": account["id"], "amount_minor": -100, "occurred_on": "2026-10-01", **overrides}
        return client.post("/transactions", json=body, headers=headers).status_code

    assert create() == 201
    assert create(amount_minor=0) == 422
    assert create(occurred_on="not-a-date") == 422
    assert create(account_id=foreign_account["id"]) == 422
    assert create(account_id=str(uuid.uuid4())) == 422
    assert create(category_id=foreign_category["id"]) == 422


def test_update_and_clearing_fields(client):
    headers = make_user(client)
    account = make_account(client, headers)
    other_account = make_account(client, headers, name="Other")
    food = make_category(client, headers, "Food")
    tx = make_transaction(client, headers, account["id"], -100, merchant="Shop", category_id=food["id"])
    url = f"/transactions/{tx['id']}"

    patched = client.patch(url, json={"amount_minor": -999, "note": "lunch"}, headers=headers).json()
    assert patched["amount_minor"] == -999 and patched["note"] == "lunch"
    assert patched["merchant"] == "Shop" and patched["category_id"] == food["id"]  # untouched

    cleared = client.patch(url, json={"category_id": None, "merchant": None}, headers=headers).json()
    assert cleared["category_id"] is None and cleared["merchant"] is None

    moved = client.patch(url, json={"account_id": other_account["id"]}, headers=headers).json()
    assert moved["account_id"] == other_account["id"]

    for bad in ({"amount_minor": None}, {"amount_minor": 0}, {"occurred_on": None}, {"account_id": str(uuid.uuid4())}):
        assert client.patch(url, json=bad, headers=headers).status_code == 422, bad


def test_soft_delete_and_sync_mode(client):
    headers = make_user(client)
    account = make_account(client, headers)
    kept = make_transaction(client, headers, account["id"])
    gone = make_transaction(client, headers, account["id"])

    assert client.delete(f"/transactions/{gone['id']}", headers=headers).status_code == 204
    assert client.get(f"/transactions/{gone['id']}", headers=headers).status_code == 404
    assert [t["id"] for t in client.get("/transactions", headers=headers).json()] == [kept["id"]]

    changes = client.get("/transactions", params=EVERYTHING, headers=headers).json()
    assert {t["id"]: t["deleted_at"] is not None for t in changes} == {kept["id"]: False, gone["id"]: True}

    future = {"updated_since": "2999-01-01T00:00:00Z"}
    assert client.get("/transactions", params=future, headers=headers).json() == []


def test_client_supplied_id_is_used_and_not_reusable(client):
    headers = make_user(client)
    account = make_account(client, headers)
    tx_id = str(uuid.uuid4())
    assert make_transaction(client, headers, account["id"], id=tx_id)["id"] == tx_id
    again = client.post(
        "/transactions",
        json={"id": tx_id, "account_id": account["id"], "amount_minor": -1, "occurred_on": "2026-10-01"},
        headers=headers,
    )
    assert again.status_code == 409


def test_users_cannot_see_each_others_transactions(client):
    alice = make_user(client, email="alice@example.com")
    bob = make_user(client, email="bob@example.com")
    tx = make_transaction(client, alice, make_account(client, alice)["id"])

    assert client.get("/transactions", headers=bob).json() == []
    assert client.get("/transactions", params=EVERYTHING, headers=bob).json() == []
    url = f"/transactions/{tx['id']}"
    assert client.get(url, headers=bob).status_code == 404
    assert client.patch(url, json={"note": "x"}, headers=bob).status_code == 404
    assert client.delete(url, headers=bob).status_code == 404
    assert client.get(url, headers=alice).status_code == 200
