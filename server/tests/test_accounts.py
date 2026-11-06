import uuid

from tests.conftest import ACCOUNT, make_account, make_transaction, make_user


def test_crud_flow(client):
    headers = make_user(client)

    created = client.post("/accounts", json=ACCOUNT, headers=headers)
    assert created.status_code == 201, created.text
    account = created.json()
    assert account["opening_balance_minor"] == 150000
    assert account["deleted_at"] is None

    assert [a["id"] for a in client.get("/accounts", headers=headers).json()] == [account["id"]]

    patched = client.patch(f"/accounts/{account['id']}", json={"name": "Everyday"}, headers=headers)
    assert patched.status_code == 200
    assert patched.json()["name"] == "Everyday"
    assert patched.json()["currency"] == "EUR"

    assert client.delete(f"/accounts/{account['id']}", headers=headers).status_code == 204
    assert client.get(f"/accounts/{account['id']}", headers=headers).status_code == 404
    assert client.get("/accounts", headers=headers).json() == []


def test_currency_cannot_change(client):
    headers = make_user(client)
    account = make_account(client, headers)
    r = client.patch(f"/accounts/{account['id']}", json={"currency": "USD"}, headers=headers)
    assert r.status_code == 422


def test_client_supplied_id_is_used_and_not_reusable(client):
    headers = make_user(client)
    account_id = str(uuid.uuid4())
    first = client.post("/accounts", json={**ACCOUNT, "id": account_id}, headers=headers)
    assert first.status_code == 201
    assert first.json()["id"] == account_id
    again = client.post("/accounts", json={**ACCOUNT, "id": account_id}, headers=headers)
    assert again.status_code == 409


def test_users_cannot_see_each_others_accounts(client):
    alice = make_user(client, email="alice@example.com")
    bob = make_user(client, email="bob@example.com")
    account = make_account(client, alice)

    assert client.get("/accounts", headers=bob).json() == []
    assert client.get(f"/accounts/{account['id']}", headers=bob).status_code == 404
    assert client.patch(f"/accounts/{account['id']}", json={"name": "x"}, headers=bob).status_code == 404
    assert client.delete(f"/accounts/{account['id']}", headers=bob).status_code == 404
    # Still intact for the owner
    assert client.get(f"/accounts/{account['id']}", headers=alice).status_code == 200


def test_validation(client):
    headers = make_user(client)
    for bad in (
        {**ACCOUNT, "currency": "eur"},
        {**ACCOUNT, "type": "piggybank"},
        {**ACCOUNT, "name": ""},
        {**ACCOUNT, "opening_balance_minor": 10**18},
    ):
        assert client.post("/accounts", json=bad, headers=headers).status_code == 422


def test_updated_since_includes_deleted_rows(client):
    headers = make_user(client)
    account = make_account(client, headers)
    client.delete(f"/accounts/{account['id']}", headers=headers)

    everything = client.get("/accounts", params={"updated_since": "2000-01-01T00:00:00Z"}, headers=headers)
    assert [a["id"] for a in everything.json()] == [account["id"]]
    assert everything.json()[0]["deleted_at"] is not None

    nothing = client.get("/accounts", params={"updated_since": "2999-01-01T00:00:00Z"}, headers=headers)
    assert nothing.json() == []


def test_deleting_an_account_soft_deletes_its_transactions(client):
    headers = make_user(client)
    keep = make_account(client, headers, name="Keep")
    drop = make_account(client, headers, name="Drop")
    kept_tx = make_transaction(client, headers, keep["id"])
    dropped_tx = make_transaction(client, headers, drop["id"])

    client.delete(f"/accounts/{drop['id']}", headers=headers)

    assert client.get(f"/transactions/{dropped_tx['id']}", headers=headers).status_code == 404
    assert client.get(f"/transactions/{kept_tx['id']}", headers=headers).status_code == 200
    changes = client.get("/transactions", params={"updated_since": "2000-01-01T00:00:00Z"}, headers=headers).json()
    deleted = {t["id"]: t["deleted_at"] for t in changes}
    assert deleted[dropped_tx["id"]] is not None
    assert deleted[kept_tx["id"]] is None
