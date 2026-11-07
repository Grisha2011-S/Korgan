from __future__ import annotations

import hashlib
import http
import logging
import os
import secrets
import sqlite3
import threading
import time
import uuid
from contextlib import contextmanager
from pathlib import Path
from typing import Literal, Optional

from fastapi import Depends, FastAPI, Header, HTTPException
from pydantic import BaseModel, Field

from push import Pusher

HERE = Path(__file__).resolve().parent
DB_PATH = Path(os.environ.get("KORGAN_DB", HERE / "korgan.db"))
SERVICE_ACCOUNT = Path(os.environ.get("KORGAN_FIREBASE_KEY", HERE / "firebase-service-account.json"))

PAIR_CODE_TTL_S = 10 * 60
MAX_PAIR_FAILURES_PER_HOUR = 10
ALERT_HISTORY_LIMIT = 50

log = logging.getLogger("korgan")


class Store:
    def __init__(self, path: Path):
        self._lock = threading.Lock()
        self._db = sqlite3.connect(str(path), check_same_thread=False)
        self._db.row_factory = sqlite3.Row
        self._db.executescript(
            """
            PRAGMA journal_mode=WAL;
            CREATE TABLE IF NOT EXISTS devices (
                id TEXT PRIMARY KEY,
                secret_hash TEXT NOT NULL,
                role TEXT NOT NULL,
                name TEXT NOT NULL,
                platform TEXT NOT NULL,
                push_token TEXT,
                created REAL NOT NULL,
                last_seen REAL NOT NULL
            );
            CREATE TABLE IF NOT EXISTS links (
                wearer_id TEXT NOT NULL,
                caregiver_id TEXT NOT NULL,
                created REAL NOT NULL,
                PRIMARY KEY (wearer_id, caregiver_id)
            );
            CREATE TABLE IF NOT EXISTS pair_codes (
                code TEXT PRIMARY KEY,
                caregiver_id TEXT NOT NULL,
                expires REAL NOT NULL
            );
            CREATE TABLE IF NOT EXISTS pair_failures (
                device_id TEXT NOT NULL,
                at REAL NOT NULL
            );
            CREATE TABLE IF NOT EXISTS alerts (
                id TEXT PRIMARY KEY,
                wearer_id TEXT NOT NULL,
                kind TEXT NOT NULL,
                title TEXT NOT NULL,
                body TEXT NOT NULL,
                latitude REAL,
                longitude REAL,
                accuracy REAL,
                address TEXT NOT NULL,
                battery INTEGER,
                created REAL NOT NULL,
                acked_by TEXT,
                acked_name TEXT,
                acked_at REAL
            );
            CREATE INDEX IF NOT EXISTS alerts_by_wearer ON alerts (wearer_id, created);
            """
        )

    @contextmanager
    def tx(self):
        with self._lock:
            try:
                yield self._db
                self._db.commit()
            except Exception:
                self._db.rollback()
                raise


def hash_secret(secret: str) -> str:
    return hashlib.sha256(secret.encode()).hexdigest()


Role = Literal["wearer", "caregiver"]
Platform = Literal["android", "ios"]
AlertKind = Literal["FALL", "SOS", "TEST"]


class RegisterIn(BaseModel):
    role: Role
    name: str = Field(min_length=1, max_length=60)
    platform: Platform = "android"
    push_token: Optional[str] = Field(default=None, max_length=4096)


class RegisterOut(BaseModel):
    device_id: str
    token: str


class ProfileIn(BaseModel):
    name: Optional[str] = Field(default=None, min_length=1, max_length=60)
    push_token: Optional[str] = Field(default=None, max_length=4096)


class PairCodeOut(BaseModel):
    code: str
    expires_in: int


class PairJoinIn(BaseModel):
    code: str = Field(min_length=6, max_length=6)


class LinkOut(BaseModel):
    device_id: str
    name: str
    role: Role
    has_push: bool


class AlertIn(BaseModel):
    kind: AlertKind
    title: str = Field(max_length=120)
    body: str = Field(max_length=1000)
    latitude: Optional[float] = None
    longitude: Optional[float] = None
    accuracy: Optional[float] = None
    address: str = Field(default="", max_length=300)
    battery: Optional[int] = None


class AlertSentOut(BaseModel):
    alert_id: str
    linked: int
    pushed: int
    push_mode: str


class AlertOut(BaseModel):
    alert_id: str
    wearer_id: str
    wearer_name: str
    kind: AlertKind
    title: str
    body: str
    latitude: Optional[float]
    longitude: Optional[float]
    accuracy: Optional[float]
    address: str
    battery: Optional[int]
    created: float
    acked_by_name: Optional[str]
    acked_at: Optional[float]


def create_app(db_path: Path = DB_PATH, service_account: Path = SERVICE_ACCOUNT) -> FastAPI:
    store = Store(db_path)
    pusher = Pusher(service_account)
    app = FastAPI(title="Korgan server", version="1.0")

    def current_device(authorization: str = Header(default="")) -> sqlite3.Row:
        scheme, _, value = authorization.partition(" ")
        device_id, _, secret = value.partition(".")
        if scheme.lower() != "bearer" or not device_id or not secret:
            raise HTTPException(401, "missing token")
        with store.tx() as db:
            row = db.execute("SELECT * FROM devices WHERE id = ?", (device_id,)).fetchone()
            if row is None or not secrets.compare_digest(row["secret_hash"], hash_secret(secret)):
                raise HTTPException(401, "bad token")
            db.execute("UPDATE devices SET last_seen = ? WHERE id = ?", (time.time(), device_id))
        return row

    def require_role(row: sqlite3.Row, role: str):
        if row["role"] != role:
            raise HTTPException(403, f"only a {role} can do this")

    def alert_out(db, row) -> AlertOut:
        wearer = db.execute("SELECT name FROM devices WHERE id = ?", (row["wearer_id"],)).fetchone()
        return AlertOut(
            alert_id=row["id"], wearer_id=row["wearer_id"],
            wearer_name=wearer["name"] if wearer else "?",
            kind=row["kind"], title=row["title"], body=row["body"],
            latitude=row["latitude"], longitude=row["longitude"], accuracy=row["accuracy"],
            address=row["address"], battery=row["battery"], created=row["created"],
            acked_by_name=row["acked_name"], acked_at=row["acked_at"],
        )

    @app.get("/health")
    def health():
        return {"ok": True, "push_mode": pusher.mode}

    @app.post("/api/register", response_model=RegisterOut)
    def register(body: RegisterIn):
        device_id = uuid.uuid4().hex
        secret = secrets.token_urlsafe(32)
        now = time.time()
        with store.tx() as db:
            db.execute(
                "INSERT INTO devices VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                (device_id, hash_secret(secret), body.role, body.name.strip(), body.platform,
                 body.push_token, now, now),
            )
        log.info("registered %s %s (%s)", body.role, body.name, body.platform)
        return RegisterOut(device_id=device_id, token=f"{device_id}.{secret}")

    @app.patch("/api/me")
    def update_me(body: ProfileIn, me=Depends(current_device)):
        with store.tx() as db:
            if body.name is not None:
                db.execute("UPDATE devices SET name = ? WHERE id = ?", (body.name.strip(), me["id"]))
            if body.push_token is not None:
                db.execute("UPDATE devices SET push_token = ? WHERE id = ?",
                           (body.push_token or None, me["id"]))
        return {"ok": True}

    @app.post("/api/pair/code", response_model=PairCodeOut)
    def new_pair_code(me=Depends(current_device)):
        require_role(me, "caregiver")
        now = time.time()
        with store.tx() as db:
            db.execute("DELETE FROM pair_codes WHERE expires < ? OR caregiver_id = ?", (now, me["id"]))
            while True:
                code = f"{secrets.randbelow(1_000_000):06d}"
                if db.execute("SELECT 1 FROM pair_codes WHERE code = ?", (code,)).fetchone() is None:
                    break
            db.execute("INSERT INTO pair_codes VALUES (?, ?, ?)", (code, me["id"], now + PAIR_CODE_TTL_S))
        return PairCodeOut(code=code, expires_in=PAIR_CODE_TTL_S)

    @app.post("/api/pair/join", response_model=LinkOut)
    def join_pair(body: PairJoinIn, me=Depends(current_device)):
        require_role(me, "wearer")
        now = time.time()
        with store.tx() as db:
            failures = db.execute(
                "SELECT COUNT(*) FROM pair_failures WHERE device_id = ? AND at > ?",
                (me["id"], now - 3600),
            ).fetchone()[0]
            if failures >= MAX_PAIR_FAILURES_PER_HOUR:
                raise HTTPException(429, "too many wrong codes, try again later")
            row = db.execute(
                "SELECT caregiver_id FROM pair_codes WHERE code = ? AND expires >= ?",
                (body.code, now),
            ).fetchone()
            if row is None:
                db.execute("INSERT INTO pair_failures VALUES (?, ?)", (me["id"], now))
                caregiver = None
            else:
                caregiver = db.execute("SELECT * FROM devices WHERE id = ?", (row["caregiver_id"],)).fetchone()
                db.execute("DELETE FROM pair_codes WHERE code = ?", (body.code,))
                db.execute("INSERT OR IGNORE INTO links VALUES (?, ?, ?)", (me["id"], caregiver["id"], now))
        if caregiver is None:
            raise HTTPException(404, "code not found or expired")
        pusher.send(caregiver["push_token"], caregiver["platform"],
                    data={"type": "linked", "name": me["name"]},
                    title="Korgan", body=f"{me['name']} ✓")
        return LinkOut(device_id=caregiver["id"], name=caregiver["name"], role="caregiver",
                       has_push=bool(caregiver["push_token"]))

    @app.get("/api/links", response_model=list[LinkOut])
    def list_links(me=Depends(current_device)):
        mine, other = ("wearer_id", "caregiver_id") if me["role"] == "wearer" else ("caregiver_id", "wearer_id")
        with store.tx() as db:
            rows = db.execute(
                f"SELECT d.* FROM links l JOIN devices d ON d.id = l.{other} "
                f"WHERE l.{mine} = ? ORDER BY l.created",
                (me["id"],),
            ).fetchall()
        return [LinkOut(device_id=r["id"], name=r["name"], role=r["role"], has_push=bool(r["push_token"]))
                for r in rows]

    @app.delete("/api/links/{other_id}")
    def delete_link(other_id: str, me=Depends(current_device)):
        with store.tx() as db:
            db.execute(
                "DELETE FROM links WHERE (wearer_id = ? AND caregiver_id = ?) OR (wearer_id = ? AND caregiver_id = ?)",
                (me["id"], other_id, other_id, me["id"]),
            )
        return {"ok": True}

    @app.post("/api/alerts", response_model=AlertSentOut)
    def send_alert(body: AlertIn, me=Depends(current_device)):
        require_role(me, "wearer")
        alert_id = uuid.uuid4().hex
        with store.tx() as db:
            db.execute(
                "INSERT INTO alerts (id, wearer_id, kind, title, body, latitude, longitude, accuracy, "
                "address, battery, created) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                (alert_id, me["id"], body.kind, body.title, body.body, body.latitude, body.longitude,
                 body.accuracy, body.address, body.battery, time.time()),
            )
            caregivers = db.execute(
                "SELECT d.* FROM links l JOIN devices d ON d.id = l.caregiver_id WHERE l.wearer_id = ?",
                (me["id"],),
            ).fetchall()
        data = {
            "type": "alert", "alert_id": alert_id, "kind": body.kind,
            "wearer_id": me["id"], "wearer_name": me["name"],
            "title": body.title, "body": body.body, "address": body.address,
            "latitude": "" if body.latitude is None else repr(body.latitude),
            "longitude": "" if body.longitude is None else repr(body.longitude),
            "battery": "" if body.battery is None else str(body.battery),
        }
        pushed = sum(
            1 for c in caregivers
            if pusher.send(c["push_token"], c["platform"], data=data, title=body.title, body=body.body,
                           on_invalid_token=lambda cid=c["id"]: _drop_token(store, cid))
        )
        log.warning("ALERT %s from %s -> %d caregiver(s), %d pushed", body.kind, me["name"], len(caregivers), pushed)
        return AlertSentOut(alert_id=alert_id, linked=len(caregivers), pushed=pushed, push_mode=pusher.mode)

    @app.get("/api/alerts", response_model=list[AlertOut])
    def list_alerts(since: float = 0.0, me=Depends(current_device)):
        with store.tx() as db:
            if me["role"] == "caregiver":
                rows = db.execute(
                    "SELECT a.* FROM alerts a JOIN links l ON l.wearer_id = a.wearer_id "
                    "WHERE l.caregiver_id = ? AND a.created > ? ORDER BY a.created DESC LIMIT ?",
                    (me["id"], since, ALERT_HISTORY_LIMIT),
                ).fetchall()
            else:
                rows = db.execute(
                    "SELECT * FROM alerts WHERE wearer_id = ? AND created > ? ORDER BY created DESC LIMIT ?",
                    (me["id"], since, ALERT_HISTORY_LIMIT),
                ).fetchall()
            return [alert_out(db, r) for r in rows]

    @app.get("/api/alerts/{alert_id}", response_model=AlertOut)
    def get_alert(alert_id: str, me=Depends(current_device)):
        with store.tx() as db:
            row = _visible_alert(db, alert_id, me)
            return alert_out(db, row)

    @app.post("/api/alerts/{alert_id}/ack", response_model=AlertOut)
    def ack_alert(alert_id: str, me=Depends(current_device)):
        require_role(me, "caregiver")
        with store.tx() as db:
            row = _visible_alert(db, alert_id, me)
            if row["acked_by"] is None:
                db.execute("UPDATE alerts SET acked_by = ?, acked_name = ?, acked_at = ? WHERE id = ?",
                           (me["id"], me["name"], time.time(), alert_id))
            row = db.execute("SELECT * FROM alerts WHERE id = ?", (alert_id,)).fetchone()
            wearer = db.execute("SELECT * FROM devices WHERE id = ?", (row["wearer_id"],)).fetchone()
            out = alert_out(db, row)
        pusher.send(wearer["push_token"], wearer["platform"],
                    data={"type": "ack", "alert_id": alert_id, "name": out.acked_by_name or me["name"]},
                    title="Korgan", body=f"{out.acked_by_name or me['name']} ✓")
        return out

    return app


def _visible_alert(db, alert_id: str, me) -> sqlite3.Row:
    row = db.execute("SELECT * FROM alerts WHERE id = ?", (alert_id,)).fetchone()
    if row is None:
        raise HTTPException(404, "alert not found")
    if me["role"] == "wearer":
        allowed = row["wearer_id"] == me["id"]
    else:
        allowed = db.execute("SELECT 1 FROM links WHERE wearer_id = ? AND caregiver_id = ?",
                             (row["wearer_id"], me["id"])).fetchone() is not None
    if not allowed:
        raise HTTPException(404, "alert not found")
    return row


def _drop_token(store: Store, device_id: str):
    with store.tx() as db:
        db.execute("UPDATE devices SET push_token = NULL WHERE id = ?", (device_id,))


if __name__ == "__main__":
    import uvicorn
    print("http://192.168.10.18:8000")

    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    uvicorn.run(create_app(), host=os.environ.get("KORGAN_HOST", "0.0.0.0"),
                port=int(os.environ.get("KORGAN_PORT", "8000")))
