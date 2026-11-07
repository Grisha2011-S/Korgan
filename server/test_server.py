from fastapi.testclient import TestClient

from korgan_server import create_app


def make_client(tmp_path):
    return TestClient(create_app(tmp_path / "test.db", tmp_path / "no-key.json"))


def register(client, role, name):
    r = client.post("/api/register", json={"role": role, "name": name, "push_token": "tok-" + name})
    assert r.status_code == 200
    return {"Authorization": "Bearer " + r.json()["token"]}


def test_full_alert_flow(tmp_path):
    c = make_client(tmp_path)
    assert c.get("/health").json() == {"ok": True, "push_mode": "dry-run"}

    granny = register(c, "wearer", "Бабушка")
    aigul = register(c, "caregiver", "Айгуль")

    code = c.post("/api/pair/code", headers=aigul).json()["code"]
    assert len(code) == 6
    linked = c.post("/api/pair/join", json={"code": code}, headers=granny).json()
    assert linked["name"] == "Айгуль" and linked["has_push"]
    # a code works only once
    assert c.post("/api/pair/join", json={"code": code}, headers=granny).status_code == 404

    assert [l["name"] for l in c.get("/api/links", headers=granny).json()] == ["Айгуль"]
    assert [l["name"] for l in c.get("/api/links", headers=aigul).json()] == ["Бабушка"]

    sent = c.post("/api/alerts", headers=granny, json={
        "kind": "FALL", "title": "Падение", "body": "Бабушка упала",
        "latitude": 54.87, "longitude": 69.14, "address": "ул. Абая 1",
    }).json()
    assert sent["linked"] == 1 and sent["push_mode"] == "dry-run"

    inbox = c.get("/api/alerts", headers=aigul).json()
    assert inbox[0]["alert_id"] == sent["alert_id"] and inbox[0]["wearer_name"] == "Бабушка"
    assert inbox[0]["acked_by_name"] is None

    acked = c.post(f"/api/alerts/{sent['alert_id']}/ack", headers=aigul).json()
    assert acked["acked_by_name"] == "Айгуль"
    status = c.get(f"/api/alerts/{sent['alert_id']}", headers=granny).json()
    assert status["acked_by_name"] == "Айгуль"


def test_permissions(tmp_path):
    c = make_client(tmp_path)
    granny = register(c, "wearer", "Бабушка")
    stranger = register(c, "caregiver", "Чужой")

    assert c.get("/api/links").status_code == 401
    assert c.get("/api/links", headers={"Authorization": "Bearer x.y"}).status_code == 401
    # roles are enforced
    assert c.post("/api/pair/code", headers=granny).status_code == 403
    assert c.post("/api/alerts", headers=stranger, json={"kind": "SOS", "title": "t", "body": "b"}).status_code == 403

    alert = c.post("/api/alerts", headers=granny, json={"kind": "SOS", "title": "t", "body": "b"}).json()
    assert alert["linked"] == 0
    # an unlinked caregiver can neither see nor acknowledge it
    assert c.get("/api/alerts", headers=stranger).json() == []
    assert c.post(f"/api/alerts/{alert['alert_id']}/ack", headers=stranger).status_code == 404


def test_wrong_codes_are_rate_limited(tmp_path):
    c = make_client(tmp_path)
    granny = register(c, "wearer", "Бабушка")
    codes = [c.post("/api/pair/join", json={"code": "000000"}, headers=granny).status_code for _ in range(11)]
    assert codes[:10] == [404] * 10 and codes[10] == 429
