import uuid

from tests.conftest import make_account, make_category, make_transaction, make_user

EVERYTHING = {"updated_since": "2000-01-01T00:00:00Z"}


def new_id():
    return str(uuid.uuid4())


def put_leg(client, headers, account_id, amount, group_id, tx_id=None, **extra):
    body = {
        "account_id": account_id,
        "amount_minor": amount,
        "occurred_on": "2026-10-24",
        "transfer_group_id": group_id,
        **extra,
    }
    return client.put(f"/transactions/{tx_id or new_id()}", json=body, headers=headers)


def test_both_legs_are_stored_and_returned_with_the_group_id(client):
    headers = make_user(client)
    bank = make_account(client, headers, name="Bank")
    card = make_account(client, headers, name="Card", type="credit_card")
    group = new_id()

    out = put_leg(client, headers, bank["id"], -2_000_000, group)
    into = put_leg(client, headers, card["id"], 2_000_000, group)

    assert out.status_code == 201 and into.status_code == 201
    assert out.json()["transfer_group_id"] == group and into.json()["transfer_group_id"] == group
    listed = client.get("/transactions", headers=headers).json()
    assert {t["transfer_group_id"] for t in listed} == {group}
    assert sorted(t["amount_minor"] for t in listed) == [-2_000_000, 2_000_000]


def test_ordinary_transactions_have_no_group(client):
    headers = make_user(client)
    account = make_account(client, headers)
    assert make_transaction(client, headers, account["id"])["transfer_group_id"] is None


def test_a_transfer_leg_cannot_have_a_category(client):
    headers = make_user(client)
    account = make_account(client, headers)
    category = make_category(client, headers)
    group = new_id()

    assert put_leg(client, headers, account["id"], -100, group, category_id=category["id"]).status_code == 422
    assert client.post(
        "/transactions",
        json={"account_id": account["id"], "amount_minor": -100, "occurred_on": "2026-10-24",
              "transfer_group_id": group, "category_id": category["id"]},
        headers=headers,
    ).status_code == 422

    leg = put_leg(client, headers, account["id"], -100, group)
    assert leg.status_code == 201
    assert client.patch(f"/transactions/{leg.json()['id']}", json={"category_id": category["id"]}, headers=headers).status_code == 422
    # Clearing it is fine, and so are other edits
    assert client.patch(f"/transactions/{leg.json()['id']}", json={"note": "rent share"}, headers=headers).status_code == 200


def test_replacing_a_leg_with_put_is_idempotent(client):
    headers = make_user(client)
    account = make_account(client, headers)
    group, tx_id = new_id(), new_id()
    first = put_leg(client, headers, account["id"], -500, group, tx_id=tx_id)
    again = put_leg(client, headers, account["id"], -500, group, tx_id=tx_id)
    assert first.status_code == 201 and again.status_code == 200
    assert len(client.get("/transactions", headers=headers).json()) == 1


def test_deleting_one_leg_deletes_the_other(client):
    headers = make_user(client)
    bank = make_account(client, headers, name="Bank")
    card = make_account(client, headers, name="Card")
    plain = make_transaction(client, headers, bank["id"])
    group = new_id()
    out = put_leg(client, headers, bank["id"], -700, group).json()
    into = put_leg(client, headers, card["id"], 700, group).json()

    assert client.delete(f"/transactions/{out['id']}", headers=headers).status_code == 204

    assert client.get(f"/transactions/{into['id']}", headers=headers).status_code == 404  # the other leg went too
    assert client.get(f"/transactions/{plain['id']}", headers=headers).status_code == 200  # unrelated one stays
    changes = client.get("/transactions", params=EVERYTHING, headers=headers).json()
    deleted = {t["id"]: t["deleted_at"] is not None for t in changes}
    assert deleted == {plain["id"]: False, out["id"]: True, into["id"]: True}
    # A second DELETE for the already-deleted leg is a plain 404, which sync clients treat as done
    assert client.delete(f"/transactions/{into['id']}", headers=headers).status_code == 404


def test_deleting_an_account_still_deletes_its_transfer_legs(client):
    headers = make_user(client)
    bank = make_account(client, headers, name="Bank")
    card = make_account(client, headers, name="Card")
    group = new_id()
    out = put_leg(client, headers, bank["id"], -700, group).json()
    into = put_leg(client, headers, card["id"], 700, group).json()

    client.delete(f"/accounts/{card['id']}", headers=headers)

    assert client.get(f"/transactions/{into['id']}", headers=headers).status_code == 404
    assert client.get(f"/transactions/{out['id']}", headers=headers).status_code == 200  # the other side is untouched


def test_group_ids_are_per_user_when_deleting(client):
    alice = make_user(client, email="alice@example.com")
    bob = make_user(client, email="bob@example.com")
    shared_group = new_id()  # even if both users somehow used the same group id
    a_leg = put_leg(client, alice, make_account(client, alice)["id"], -1, shared_group).json()
    b_leg = put_leg(client, bob, make_account(client, bob)["id"], -1, shared_group).json()

    client.delete(f"/transactions/{a_leg['id']}", headers=alice)

    assert client.get(f"/transactions/{b_leg['id']}", headers=bob).status_code == 200


def test_the_group_id_must_be_a_uuid(client):
    headers = make_user(client)
    account = make_account(client, headers)
    r = client.put(
        f"/transactions/{new_id()}",
        json={"account_id": account["id"], "amount_minor": -1, "occurred_on": "2026-10-24", "transfer_group_id": "not-a-uuid"},
        headers=headers,
    )
    assert r.status_code == 422
