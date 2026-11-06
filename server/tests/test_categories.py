import uuid

from tests.conftest import make_account, make_category, make_transaction, make_user


def test_parent_and_child(client):
    headers = make_user(client)
    food = make_category(client, headers, "Food")
    groceries = make_category(client, headers, "Groceries", parent_id=food["id"])

    assert groceries["parent_id"] == food["id"]
    names = [c["name"] for c in client.get("/categories", headers=headers).json()]
    assert names == ["Food", "Groceries"]


def test_hierarchy_rules(client):
    headers = make_user(client)
    other = make_user(client, email="other@example.com")
    food = make_category(client, headers, "Food")
    groceries = make_category(client, headers, "Groceries", parent_id=food["id"])
    salary = make_category(client, headers, "Salary", kind="income")
    foreign = make_category(client, other, "Foreign")

    def create(**body):
        return client.post("/categories", json=body, headers=headers).status_code

    assert create(name="Bio", kind="expense", parent_id=groceries["id"]) == 422  # three levels
    assert create(name="Bonus", kind="income", parent_id=food["id"]) == 422  # kind mismatch
    assert create(name="X", kind="expense", parent_id=foreign["id"]) == 422  # someone else's
    assert create(name="X", kind="expense", parent_id=str(uuid.uuid4())) == 422  # doesn't exist
    assert salary["parent_id"] is None


def test_update_rules(client):
    headers = make_user(client)
    food = make_category(client, headers, "Food")
    fun = make_category(client, headers, "Fun")
    groceries = make_category(client, headers, "Groceries", parent_id=food["id"])

    renamed = client.patch(f"/categories/{fun['id']}", json={"name": "Leisure"}, headers=headers)
    assert renamed.json()["name"] == "Leisure"

    moved = client.patch(f"/categories/{fun['id']}", json={"parent_id": food["id"]}, headers=headers)
    assert moved.json()["parent_id"] == food["id"]

    top_level = client.patch(f"/categories/{fun['id']}", json={"parent_id": None}, headers=headers)
    assert top_level.json()["parent_id"] is None

    # A category that has subcategories can't become a subcategory, and can't be its own parent
    assert client.patch(f"/categories/{food['id']}", json={"parent_id": fun["id"]}, headers=headers).status_code == 422
    assert client.patch(f"/categories/{fun['id']}", json={"parent_id": fun["id"]}, headers=headers).status_code == 422

    assert client.patch(f"/categories/{groceries['id']}", json={"kind": "income"}, headers=headers).status_code == 422
    assert client.patch(f"/categories/{groceries['id']}", json={"name": None}, headers=headers).status_code == 422


def test_delete_cascades_to_children_and_uncategorizes_transactions(client):
    headers = make_user(client)
    account = make_account(client, headers)
    food = make_category(client, headers, "Food")
    groceries = make_category(client, headers, "Groceries", parent_id=food["id"])
    tx = make_transaction(client, headers, account["id"], category_id=groceries["id"])

    assert client.delete(f"/categories/{food['id']}", headers=headers).status_code == 204

    assert client.get(f"/categories/{food['id']}", headers=headers).status_code == 404
    assert client.get(f"/categories/{groceries['id']}", headers=headers).status_code == 404
    assert client.get("/categories", headers=headers).json() == []
    assert client.get(f"/transactions/{tx['id']}", headers=headers).json()["category_id"] is None

    changes = client.get("/categories", params={"updated_since": "2000-01-01T00:00:00Z"}, headers=headers).json()
    assert len(changes) == 2 and all(c["deleted_at"] for c in changes)


def test_users_cannot_see_each_others_categories(client):
    alice = make_user(client, email="alice@example.com")
    bob = make_user(client, email="bob@example.com")
    category = make_category(client, alice, "Food")

    assert client.get("/categories", headers=bob).json() == []
    assert client.get(f"/categories/{category['id']}", headers=bob).status_code == 404
    assert client.delete(f"/categories/{category['id']}", headers=bob).status_code == 404
