from app.api.deps import SessionDep
from app.db.models import CategoryKind
from app.repositories.categories import CategoryRepository
from app.services.default_categories import DefaultCategory, DefaultCategorySeeder
from tests.conftest import build_client, make_user


def category_list(client, headers):
    return client.get("/categories", headers=headers).json()


def test_new_users_get_the_default_categories(seeded_client):
    headers = make_user(seeded_client)
    by_name = {c["name"]: c for c in category_list(seeded_client, headers)}

    for name in ("Groceries", "Clothes", "Rent", "Entertainment", "Health"):
        assert by_name[name]["kind"] == "expense"
        assert by_name[name]["parent_id"] is None
    assert by_name["Salary"]["kind"] == "income"


def test_every_user_gets_their_own_copy(seeded_client):
    alice = make_user(seeded_client, email="alice@example.com")
    bob = make_user(seeded_client, email="bob@example.com")
    alice_categories = category_list(seeded_client, alice)
    bob_categories = category_list(seeded_client, bob)

    assert {c["id"] for c in alice_categories}.isdisjoint({c["id"] for c in bob_categories})

    groceries = next(c for c in alice_categories if c["name"] == "Groceries")
    assert seeded_client.delete(f"/categories/{groceries['id']}", headers=alice).status_code == 204
    assert "Groceries" not in [c["name"] for c in category_list(seeded_client, alice)]
    assert "Groceries" in [c["name"] for c in category_list(seeded_client, bob)]


def test_users_can_customize_their_categories(seeded_client):
    headers = make_user(seeded_client)
    by_name = {c["name"]: c for c in category_list(seeded_client, headers)}

    added = seeded_client.post("/categories", json={"name": "Pets", "kind": "expense"}, headers=headers)
    assert added.status_code == 201
    seeded_client.patch(f"/categories/{by_name['Rent']['id']}", json={"name": "Housing"}, headers=headers)
    seeded_client.delete(f"/categories/{by_name['Clothes']['id']}", headers=headers)

    names = [c["name"] for c in category_list(seeded_client, headers)]
    assert "Pets" in names and "Housing" in names
    assert "Rent" not in names and "Clothes" not in names

    # Logging in again doesn't bring the deleted ones back
    again = seeded_client.post("/auth/login", data={"username": "a@example.com", "password": "correct-horse-battery"})
    assert again.status_code == 200
    assert "Clothes" not in [c["name"] for c in category_list(seeded_client, headers)]


def test_defaults_can_include_subcategories():
    def nested_seeder(session: SessionDep):
        defaults = (DefaultCategory("Entertainment", CategoryKind.EXPENSE, children=("Movies", "Concerts")),)
        return DefaultCategorySeeder(CategoryRepository(session), defaults=defaults)

    with build_client(nested_seeder) as client:
        headers = make_user(client)
        categories = category_list(client, headers)
        parent = next(c for c in categories if c["name"] == "Entertainment")
        children = [c for c in categories if c["parent_id"] == parent["id"]]
        assert sorted(c["name"] for c in children) == ["Concerts", "Movies"]
        assert all(c["kind"] == "expense" for c in children)
