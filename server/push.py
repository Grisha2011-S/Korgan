"""Push delivery through Firebase Cloud Messaging (HTTP v1), Android + iOS."""
from __future__ import annotations

import json
import logging
from pathlib import Path
from typing import Callable, Optional

log = logging.getLogger("korgan.push")

FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging"


class Pusher:
    """Sends a push to one device token. Falls back to dry-run when no key is configured."""

    def __init__(self, service_account: Path):
        self._session = None
        self._project_id = None
        if service_account.is_file():
            from google.auth.transport.requests import AuthorizedSession
            from google.oauth2 import service_account as sa

            info = json.loads(service_account.read_text(encoding="utf-8"))
            creds = sa.Credentials.from_service_account_info(info, scopes=[FCM_SCOPE])
            self._session = AuthorizedSession(creds)
            self._project_id = info["project_id"]
            log.info("FCM enabled for project %s", self._project_id)
        else:
            log.warning("%s not found: push runs in dry-run mode", service_account.name)

    @property
    def mode(self) -> str:
        return "fcm" if self._session else "dry-run"

    def send(
        self,
        token: Optional[str],
        platform: str,
        data: dict[str, str],
        title: str,
        body: str,
        on_invalid_token: Optional[Callable[[], None]] = None,
    ) -> bool:
        if not token:
            return False
        if self._session is None:
            log.info("[dry-run] push to %s…: %s", token[:12], data.get("type"))
            return False

        message: dict = {
            "token": token,
            "data": {k: str(v) for k, v in data.items()},
            # Android: data-only + high priority wakes the app even in Doze;
            # the app itself shows the full-screen alarm.
            "android": {"priority": "HIGH", "ttl": "600s"},
        }
        if platform == "ios":
            message["apns"] = {
                "headers": {"apns-priority": "10", "apns-push-type": "alert"},
                "payload": {"aps": {"alert": {"title": title, "body": body}, "sound": "default"}},
            }
        url = f"https://fcm.googleapis.com/v1/projects/{self._project_id}/messages:send"
        try:
            resp = self._session.post(url, json={"message": message}, timeout=10)
        except Exception as exc:  # network error: report as not delivered
            log.error("FCM request failed: %s", exc)
            return False
        if resp.status_code == 200:
            return True
        if resp.status_code == 404 or "UNREGISTERED" in resp.text:
            log.warning("dropping unregistered push token")
            if on_invalid_token:
                on_invalid_token()
        else:
            log.error("FCM error %s: %s", resp.status_code, resp.text[:300])
        return False
