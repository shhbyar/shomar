from tests.conftest import make_user


def test_register_login_me(client):
    headers = make_user(client)
    r = client.get("/auth/me", headers=headers)
    assert r.status_code == 200
    assert r.json()["email"] == "a@example.com"
    assert "password" not in r.text


def test_email_is_case_insensitive(client):
    make_user(client, email="Mixed@Example.com")
    r = client.post("/auth/login", data={"username": "mixed@example.com", "password": "correct-horse-battery"})
    assert r.status_code == 200


def test_duplicate_email_conflicts(client):
    make_user(client)
    r = client.post(
        "/auth/register",
        json={"email": "A@example.com", "password": "another-password", "default_currency": "EUR"},
    )
    assert r.status_code == 409


def test_wrong_password_and_unknown_user_look_the_same(client):
    make_user(client)
    bad_pw = client.post("/auth/login", data={"username": "a@example.com", "password": "nope-nope-nope"})
    unknown = client.post("/auth/login", data={"username": "ghost@example.com", "password": "nope-nope-nope"})
    assert bad_pw.status_code == unknown.status_code == 401
    assert bad_pw.json() == unknown.json()


def test_short_password_rejected(client):
    r = client.post(
        "/auth/register",
        json={"email": "b@example.com", "password": "short", "default_currency": "EUR"},
    )
    assert r.status_code == 422


def test_protected_routes_need_a_valid_token(client):
    assert client.get("/accounts").status_code == 401
    r = client.get("/accounts", headers={"Authorization": "Bearer garbage"})
    assert r.status_code == 401
