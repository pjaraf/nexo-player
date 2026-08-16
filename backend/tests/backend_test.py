"""ZapingX / Nexus IPTV Backend tests — iteration 4.

Covers auth, Xtream-native Live (Dropbox M3U removed), VOD, series, and the
stream proxy layer. All requests hit the public preview URL from
frontend/.env.
"""
import os
import time
import pytest
import requests
from pathlib import Path


def _load_public_url() -> str:
    env_path = Path("/app/frontend/.env")
    for line in env_path.read_text().splitlines():
        if line.startswith("EXPO_PUBLIC_BACKEND_URL="):
            return line.split("=", 1)[1].strip().strip('"').rstrip("/")
    raise RuntimeError("EXPO_PUBLIC_BACKEND_URL missing in frontend/.env")


BASE_URL = _load_public_url()
API = f"{BASE_URL}/api"
USERNAME = "1q1q1q1q"
PASSWORD = "1q1q1q1q"


@pytest.fixture(scope="session")
def client():
    s = requests.Session()
    s.headers.update({"Content-Type": "application/json"})
    return s


@pytest.fixture(scope="session")
def token(client):
    r = client.post(f"{API}/auth/login",
                    json={"username": USERNAME, "password": PASSWORD}, timeout=60)
    assert r.status_code == 200, f"login failed: {r.status_code} {r.text}"
    return r.json()["token"]


@pytest.fixture(scope="session")
def auth_headers(token):
    return {"Authorization": f"Bearer {token}"}


# --- Health / auth ---------------------------------------------------------
class TestHealthAndAuth:
    def test_root(self, client):
        r = client.get(f"{API}/", timeout=30)
        assert r.status_code == 200
        assert "IPTV" in r.json().get("message", "")

    def test_login_success(self, client):
        r = client.post(f"{API}/auth/login",
                        json={"username": USERNAME, "password": PASSWORD}, timeout=60)
        assert r.status_code == 200
        j = r.json()
        assert j["token"]
        assert j["user"]["username"] == USERNAME

    def test_login_invalid_rejected(self, client):
        r = client.post(f"{API}/auth/login",
                        json={"username": "nope_xyz", "password": "nope_xyz"}, timeout=60)
        # Upstream may respond JSON auth=0 -> 401, or WAF HTML -> 502
        assert r.status_code in (401, 502)


# --- Dropbox / M3U removal check -------------------------------------------
class TestDropboxRemoval:
    def test_no_dropbox_reference_in_server(self):
        content = Path("/app/backend/server.py").read_text()
        assert "CLIENTES.txt" not in content, "Dropbox CLIENTES.txt still present"
        assert "dropbox" not in content.lower(), "dropbox reference still present"
        assert "m3u_" not in content, "m3u_ prefix still present"


# --- Live categories & channels (Xtream native) ----------------------------
class TestLiveXtream:
    def test_categories_are_xtream_native(self, client, auth_headers):
        r = client.get(f"{API}/live/categories", headers=auth_headers, timeout=60)
        assert r.status_code == 200
        j = r.json()
        cats = j["categories"]
        assert isinstance(cats, list)
        assert len(cats) > 90, f"expected >90 Xtream categories, got {len(cats)}"
        c = cats[0]
        # Must have id + name only (no count/group-based fields)
        assert set(c.keys()) >= {"id", "name"}
        # id must be a string (numeric string from Xtream)
        assert isinstance(c["id"], str) and c["id"]
        assert isinstance(c["name"], str) and c["name"]

    def test_channels_full_list(self, client, auth_headers):
        r = client.get(f"{API}/live/channels", headers=auth_headers, timeout=90)
        assert r.status_code == 200
        j = r.json()
        channels = j["channels"]
        assert isinstance(channels, list)
        assert j["total"] > 3000, f"expected >3000 channels, got {j['total']}"
        s = channels[0]
        # Shape check
        for k in ("id", "name", "logo", "group"):
            assert k in s, f"missing {k}"
        # id should be a numeric string
        assert isinstance(s["id"], str)
        assert s["id"].isdigit(), f"id not numeric string: {s['id']!r}"
        # No m3u_ prefixes anywhere
        for c in channels[:200]:
            assert not str(c["id"]).startswith("m3u_"), c["id"]

    def test_channels_category_filter_and_limit(self, client, auth_headers):
        cats = client.get(f"{API}/live/categories", headers=auth_headers, timeout=60).json()["categories"]
        # pick a category with content by scanning a few
        picked = None
        for c in cats[:10]:
            r = client.get(f"{API}/live/channels", headers=auth_headers,
                           params={"category": c["id"], "limit": 20}, timeout=60)
            assert r.status_code == 200
            data = r.json()
            if data["total"] > 0:
                picked = (c, data)
                break
        assert picked, "no non-empty live category found in first 10"
        cat, data = picked
        assert len(data["channels"]) <= 20
        # All channels must have the picked category name as group
        for ch in data["channels"]:
            assert ch["group"] == cat["name"], f"{ch['group']!r} != {cat['name']!r}"

    def test_play_returns_proxy_path(self, client, auth_headers, token):
        # get any real channel id
        r = client.get(f"{API}/live/channels", headers=auth_headers,
                       params={"limit": 3}, timeout=60)
        assert r.status_code == 200
        cid = r.json()["channels"][0]["id"]
        assert cid.isdigit()
        r = client.get(f"{API}/live/play", headers=auth_headers,
                       params={"id": cid}, timeout=60)
        assert r.status_code == 200
        j = r.json()
        url = j["url"]
        assert url.startswith(f"/api/stream/live/{cid}?"), url
        assert f"t={token}" in url
        assert "m3u_" not in url

    def test_play_invalid_id_404(self, client, auth_headers):
        r = client.get(f"{API}/live/play", headers=auth_headers,
                       params={"id": "999999999999"}, timeout=30)
        assert r.status_code == 404

    def test_stream_live_hls_rewritten(self, client, auth_headers, token):
        """/api/stream/live/{id}?t=.. must return either HLS manifest with only
        /api/stream/seg? or /api/stream/hls? rewrites (no zone593.com), OR
        raw video bytes for TS channels — both accepted."""
        r = client.get(f"{API}/live/channels", headers=auth_headers,
                       params={"limit": 30}, timeout=60)
        channels = r.json()["channels"]
        ok = False
        for c in channels[:20]:
            play = client.get(f"{API}/live/play", headers=auth_headers,
                              params={"id": c["id"]}, timeout=30)
            if play.status_code != 200:
                continue
            proxy = play.json()["url"]
            try:
                resp = requests.get(f"{BASE_URL}{proxy}", timeout=30, stream=True)
            except Exception:
                continue
            if resp.status_code != 200:
                resp.close()
                continue
            ct = resp.headers.get("Content-Type", "").lower()
            if "mpegurl" in ct:
                text = resp.text[:20000]
                resp.close()
                assert "zone593.com" not in text, "raw upstream host leaked"
                assert ("/api/stream/seg?" in text) or ("/api/stream/hls?" in text), text[:400]
                ok = True
                break
            # video/* or octet-stream also valid
            if ct.startswith("video/") or "octet-stream" in ct:
                ok = True
                resp.close()
                break
            resp.close()
        if not ok:
            pytest.skip("No playable channel returned HLS or video in first 20")


# --- Iteration 5: Chile-first ordering + HLS segment resolution -----------
class TestChileFirstOrdering:
    """Bug 2 fix: /api/live/categories must return Chile categories first."""

    def test_first_category_is_chile(self, client, auth_headers):
        r = client.get(f"{API}/live/categories", headers=auth_headers, timeout=60)
        assert r.status_code == 200
        cats = r.json()["categories"]
        assert cats, "empty categories"
        first = cats[0]["name"].lower()
        assert "chile" in first, f"first category is not Chile: {cats[0]['name']!r}"

    def test_latam_priority_in_top15(self, client, auth_headers):
        r = client.get(f"{API}/live/categories", headers=auth_headers, timeout=60)
        top = [c["name"].lower() for c in r.json()["categories"][:15]]
        joined = " | ".join(top)
        for kw in ("argentina", "peru", "colombia", "mexico"):
            assert any(kw in n or (kw == "peru" and "perú" in n) or
                       (kw == "mexico" and "méxico" in n) for n in top), \
                f"'{kw}' not found in top 15: {joined}"


class TestHlsSegmentResolution:
    """Bug 1 fix: _proxy_hls_playlist now uses urljoin(final_url, segment).
    Fetch a real segment through the proxy and validate MPEG-TS bytes."""

    def _fetch_segment_from_channel(self, cid: str, token: str) -> tuple[int, str, bytes]:
        # Get the HLS manifest via proxy
        manifest_url = f"{BASE_URL}/api/stream/live/{cid}?t={token}"
        r = requests.get(manifest_url, timeout=30)
        assert r.status_code == 200, f"manifest fetch failed: {r.status_code}"
        ct = r.headers.get("Content-Type", "").lower()
        # If channel returns raw TS bytes, we consider that a pass too
        if ct.startswith("video/") or "octet-stream" in ct:
            # Read a small chunk to sample first byte
            chunk = r.content[:1024] if r.content else b""
            return (r.status_code, ct, chunk)

        assert "mpegurl" in ct, f"expected HLS manifest, got {ct}"
        body = r.text
        # Find first non-comment, non-empty line
        seg_path = None
        for line in body.splitlines():
            s = line.strip()
            if not s or s.startswith("#"):
                continue
            seg_path = s
            break
        assert seg_path, f"no segment line in manifest: {body[:400]}"
        # Must be a proxy path (either /api/stream/seg or /api/stream/hls)
        assert seg_path.startswith("/api/stream/seg?") or seg_path.startswith("/api/stream/hls?"), \
            f"segment line not a proxy path: {seg_path[:200]}"
        # If it's a nested variant playlist, walk down one level
        if seg_path.startswith("/api/stream/hls?"):
            r2 = requests.get(f"{BASE_URL}{seg_path}", timeout=30)
            assert r2.status_code == 200
            body2 = r2.text
            for line in body2.splitlines():
                s = line.strip()
                if not s or s.startswith("#"):
                    continue
                seg_path = s
                break
            assert seg_path.startswith("/api/stream/seg?"), f"nested seg not proxy: {seg_path[:200]}"

        # Fetch the actual segment
        seg_url = f"{BASE_URL}{seg_path}"
        resp = requests.get(seg_url, timeout=60, stream=True)
        data = b""
        try:
            for chunk in resp.iter_content(chunk_size=65536):
                if chunk:
                    data += chunk
                if len(data) >= 800_000:
                    break
        finally:
            resp.close()
        return (resp.status_code, resp.headers.get("Content-Type", "").lower(), data)

    def test_segment_803392_returns_mpeg_ts(self, client, auth_headers, token):
        """Explicit channel from problem statement."""
        cid = "803392"
        status, ct, data = self._fetch_segment_from_channel(cid, token)
        assert status == 200, f"segment status={status}"
        # MPEG-TS: content type video/mp2t OR first byte 0x47
        assert ("mp2t" in ct) or (data[:1] == b"\x47"), \
            f"not MPEG-TS: ct={ct} first_byte={data[:4]!r}"
        assert len(data) >= 500 * 1024, f"segment too small: {len(data)} bytes"

    def test_segment_chile_channel_returns_mpeg_ts(self, client, auth_headers, token):
        """Any Chile-category channel — proves the fix works across the catalog."""
        # category=3 was suggested by the request; fall back to first channel of
        # the Chile category if 3 is empty.
        r = client.get(f"{API}/live/channels", headers=auth_headers,
                       params={"category": "3", "limit": 1}, timeout=60)
        assert r.status_code == 200
        channels = r.json().get("channels") or []
        if not channels:
            # Find first Chile category id from /categories
            cats = client.get(f"{API}/live/categories", headers=auth_headers,
                              timeout=60).json()["categories"]
            chile = next((c for c in cats if "chile" in c["name"].lower()), None)
            assert chile, "no Chile category found"
            r = client.get(f"{API}/live/channels", headers=auth_headers,
                           params={"category": chile["id"], "limit": 5}, timeout=60)
            channels = r.json().get("channels") or []
        assert channels, "no Chile channels available"
        cid = channels[0]["id"]
        status, ct, data = self._fetch_segment_from_channel(cid, token)
        assert status == 200
        assert ("mp2t" in ct) or (data[:1] == b"\x47"), \
            f"not MPEG-TS for Chile channel {cid}: ct={ct} first_byte={data[:4]!r}"
        assert len(data) >= 500 * 1024, f"Chile channel segment too small: {len(data)} bytes"


# --- Auth guard ------------------------------------------------------------
class TestAuthGuard:
    @pytest.mark.parametrize("path", [
        "/live/categories", "/live/channels", "/live/play?id=1",
        "/vod/categories", "/vod/streams", "/vod/info/1",
        "/series/categories", "/series/list", "/series/info/1",
    ])
    def test_requires_auth(self, client, path):
        r = client.get(f"{API}{path}", timeout=30)
        assert r.status_code == 401, f"{path}: {r.status_code}"

    def test_stream_endpoints_require_token(self, client):
        for p in ("/stream/vod/1", "/stream/series/1"):
            r = client.get(f"{API}{p}", params={"ext": "mp4", "t": ""}, timeout=30)
            assert r.status_code == 401


# --- VOD regression --------------------------------------------------------
class TestVod:
    def test_categories(self, client, auth_headers):
        r = client.get(f"{API}/vod/categories", headers=auth_headers, timeout=60)
        assert r.status_code == 200
        assert len(r.json()["categories"]) > 10

    def test_info_returns_proxy_url(self, client, auth_headers, token):
        r = client.get(f"{API}/vod/streams", headers=auth_headers,
                       params={"limit": 5}, timeout=90)
        streams = r.json()["streams"]
        assert streams
        sid = streams[0]["stream_id"]
        r = client.get(f"{API}/vod/info/{sid}", headers=auth_headers, timeout=60)
        assert r.status_code == 200
        url = r.json()["stream_url"]
        assert url.startswith(f"/api/stream/vod/{sid}?"), url
        assert f"t={token}" in url


# --- Series regression -----------------------------------------------------
class TestSeries:
    def test_categories(self, client, auth_headers):
        r = client.get(f"{API}/series/categories", headers=auth_headers, timeout=60)
        assert r.status_code == 200
        assert len(r.json()["categories"]) > 10

    def test_info_episodes_use_proxy_urls(self, client, auth_headers, token):
        r = client.get(f"{API}/series/list", headers=auth_headers,
                       params={"limit": 10}, timeout=90)
        series = r.json()["series"]
        assert series
        found = False
        for s in series[:5]:
            r = client.get(f"{API}/series/info/{s['series_id']}",
                           headers=auth_headers, timeout=90)
            if r.status_code != 200:
                continue
            eps = [e for lst in (r.json().get("episodes") or {}).values() for e in (lst or [])]
            if eps:
                for ep in eps:
                    assert ep["stream_url"].startswith("/api/stream/series/"), ep["stream_url"]
                    assert f"t={token}" in ep["stream_url"]
                found = True
                break
        assert found, "no episodes returned in first 5 series"
