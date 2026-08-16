from fastapi import FastAPI, APIRouter, HTTPException, Header, Request
from fastapi.responses import StreamingResponse, PlainTextResponse
from dotenv import load_dotenv
from starlette.middleware.cors import CORSMiddleware
from motor.motor_asyncio import AsyncIOMotorClient
import os
import re
import base64
import json
import logging
import time
from pathlib import Path
from pydantic import BaseModel
from typing import List, Optional, Dict, Any, Tuple
from urllib.parse import urljoin
import httpx


ROOT_DIR = Path(__file__).parent
load_dotenv(ROOT_DIR / '.env')

# MongoDB
mongo_url = os.environ['MONGO_URL']
mongo_client = AsyncIOMotorClient(mongo_url)
db = mongo_client[os.environ['DB_NAME']]

app = FastAPI(title="Nexus Backend")
api_router = APIRouter(prefix="/api")

logger = logging.getLogger("nexus")
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------
XTREAM_HOST = "https://zone593.com:8443"
DEFAULT_UA = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 VLC/3.0.18 LibVLC/3.0.18"

_xtream_cache: Dict[Tuple[str, str, str], Dict[str, Any]] = {}
_XTREAM_TTL = 60 * 15  # 15 min


# ---------------------------------------------------------------------------
# Token helpers
# ---------------------------------------------------------------------------
def _token_encode(username: str, password: str) -> str:
    payload = json.dumps({"u": username, "p": password})
    return base64.urlsafe_b64encode(payload.encode()).decode()


def _token_decode(token: str) -> Dict[str, str]:
    try:
        raw = base64.urlsafe_b64decode(token.encode()).decode()
        data = json.loads(raw)
        return {"username": data["u"], "password": data["p"]}
    except Exception:
        raise HTTPException(status_code=401, detail="Token inválido")


def _get_creds(authorization: Optional[str]) -> Dict[str, str]:
    if not authorization:
        raise HTTPException(status_code=401, detail="Falta autorización")
    token = authorization.replace("Bearer ", "").strip()
    return _token_decode(token)


def _get_creds_from_query(token: str) -> Dict[str, str]:
    if not token:
        raise HTTPException(status_code=401, detail="Falta token")
    return _token_decode(token)


# ---------------------------------------------------------------------------
# Xtream with caching
# ---------------------------------------------------------------------------
async def _xtream_get(username: str, password: str, action: Optional[str] = None,
                      extra: Optional[Dict[str, str]] = None,
                      use_cache: bool = True) -> Any:
    key = (username, action or "", json.dumps(extra or {}, sort_keys=True))
    now = time.time()
    if use_cache:
        cached = _xtream_cache.get(key)
        if cached and now - cached["at"] < _XTREAM_TTL:
            return cached["data"]

    params = {"username": username, "password": password}
    if action:
        params["action"] = action
    if extra:
        params.update(extra)
    url = f"{XTREAM_HOST}/player_api.php"
    async with httpx.AsyncClient(timeout=30.0, verify=False) as client:
        r = await client.get(url, params=params)
        r.raise_for_status()
        data = r.json()

    if use_cache:
        _xtream_cache[key] = {"data": data, "at": now}
    return data


# ---------------------------------------------------------------------------
# Stream URL builders (return relative paths; client prefixes backend URL)
# ---------------------------------------------------------------------------
def _proxy_vod_url(stream_id: int, ext: str, token: str) -> str:
    return f"/api/stream/vod/{stream_id}?ext={ext}&t={token}"


def _proxy_series_url(ep_id: int, ext: str, token: str) -> str:
    return f"/api/stream/series/{ep_id}?ext={ext}&t={token}"


def _proxy_live_url(channel_id: str, token: str) -> str:
    # End with .m3u8 so ExoPlayer / AVPlayer auto-detects HLS format even when
    # the Content-Type header is missed on native Android builds.
    return f"/api/stream/live/{channel_id}.m3u8?t={token}"


# ---------------------------------------------------------------------------
# Models
# ---------------------------------------------------------------------------
class LoginPayload(BaseModel):
    username: str
    password: str


# ---------------------------------------------------------------------------
# Basic routes
# ---------------------------------------------------------------------------
@api_router.get("/")
async def root():
    return {"message": "Nexus Backend online"}


@api_router.post("/auth/login")
async def login(payload: LoginPayload):
    try:
        data = await _xtream_get(payload.username, payload.password, use_cache=False)
    except (httpx.HTTPError, json.JSONDecodeError, ValueError):
        raise HTTPException(status_code=401, detail="Usuario o clave incorrectos")
    user_info = data.get("user_info") or {}
    if not user_info or str(user_info.get("auth", 0)) != "1":
        raise HTTPException(status_code=401, detail="Usuario o clave incorrectos")
    token = _token_encode(payload.username, payload.password)
    return {
        "token": token,
        "user": {
            "username": user_info.get("username"),
            "status": user_info.get("status"),
            "exp_date": user_info.get("exp_date"),
            "is_trial": user_info.get("is_trial"),
            "active_cons": user_info.get("active_cons"),
            "created_at": user_info.get("created_at"),
            "max_connections": user_info.get("max_connections"),
        },
        "server": data.get("server_info", {}),
    }


# ---------------------------------------------------------------------------
# LIVE (Xtream) metadata
# ---------------------------------------------------------------------------
@api_router.get("/live/categories")
async def live_categories(authorization: Optional[str] = Header(None)):
    creds = _get_creds(authorization)
    data = await _xtream_get(creds["username"], creds["password"], "get_live_categories")
    cats = []
    for c in (data or []):
        cats.append({
            "id": str(c.get("category_id")),
            "name": c.get("category_name") or "Sin categoría",
        })

    # Preferred order: TV CHILE first (as user requested), then other Chile
    # categories, then other LATAM, then the rest.
    priority_keywords = [
        "chile",     # Chile stays clustered
        "argentina",
        "peru", "perú",
        "colombia",
        "mexico", "méxico",
        "ecuador",
        "uruguay",
        "venezuela",
        "españa", "espana", "spain",
        "estados unidos", "usa", "eeuu",
    ]

    def priority(name: str) -> Tuple[int, int]:
        low = name.lower()
        # Force "TV CHILE" (the plain TV list) to be the very first item.
        if "tv chile" in low:
            return (-1, 0)
        # Then other Chile categories, then other LATAM alphabetically.
        for i, kw in enumerate(priority_keywords):
            if kw in low:
                return (i, 0)
        return (len(priority_keywords) + 1, 0)

    cats.sort(key=lambda c: (priority(c["name"]), c["name"].lower()))
    return {"categories": cats, "total": len(cats)}


@api_router.get("/live/channels")
async def live_channels(category: Optional[str] = None,
                        q: Optional[str] = None,
                        limit: int = 0,
                        authorization: Optional[str] = Header(None)):
    creds = _get_creds(authorization)
    extra = {"category_id": category} if category and category != "ALL" else None
    data = await _xtream_get(creds["username"], creds["password"], "get_live_streams", extra)
    items = data or []
    if q:
        ql = q.lower()
        items = [x for x in items if ql in (x.get("name") or "").lower()]

    cat_lookup: Dict[str, str] = {}
    try:
        cats = await _xtream_get(creds["username"], creds["password"], "get_live_categories")
        for c in (cats or []):
            cat_lookup[str(c.get("category_id"))] = c.get("category_name") or ""
    except Exception:
        pass

    channels = []
    for x in items:
        sid = x.get("stream_id")
        if sid is None:
            continue
        channels.append({
            "id": str(sid),
            "name": x.get("name") or "Sin nombre",
            "logo": x.get("stream_icon") or "",
            "group": cat_lookup.get(str(x.get("category_id")), ""),
            "num": x.get("num"),
            "epg_id": x.get("epg_channel_id"),
        })
    total = len(channels)
    if limit and limit > 0:
        channels = channels[:limit]
    return {"channels": channels, "total": total}


@api_router.get("/live/play")
async def live_play(id: str, authorization: Optional[str] = Header(None)):
    creds = _get_creds(authorization)
    token = (authorization or "").replace("Bearer ", "").strip()
    try:
        streams = await _xtream_get(creds["username"], creds["password"], "get_live_streams")
        found = next((s for s in (streams or []) if str(s.get("stream_id")) == str(id)), None)
    except Exception:
        found = None
    if not found:
        raise HTTPException(status_code=404, detail="Canal no encontrado")
    return {
        "url": _proxy_live_url(str(id), token),
        "name": found.get("name") or "Canal",
        "logo": found.get("stream_icon") or "",
    }


# ---------------------------------------------------------------------------
# VOD metadata
# ---------------------------------------------------------------------------
@api_router.get("/vod/categories")
async def vod_categories(authorization: Optional[str] = Header(None)):
    creds = _get_creds(authorization)
    data = await _xtream_get(creds["username"], creds["password"], "get_vod_categories")
    return {"categories": data or []}


@api_router.get("/vod/streams")
async def vod_streams(category_id: Optional[str] = None,
                      q: Optional[str] = None,
                      limit: int = 0,
                      authorization: Optional[str] = Header(None)):
    creds = _get_creds(authorization)
    extra = {"category_id": category_id} if category_id and category_id != "ALL" else None
    data = await _xtream_get(creds["username"], creds["password"], "get_vod_streams", extra)
    items = data or []
    if q:
        ql = q.lower()
        items = [x for x in items if ql in (x.get("name") or "").lower()]
    total = len(items)
    if limit and limit > 0:
        items = items[:limit]
    return {"streams": items, "total": total}


@api_router.get("/vod/info/{stream_id}")
async def vod_info(stream_id: str, authorization: Optional[str] = Header(None)):
    creds = _get_creds(authorization)
    token = (authorization or "").replace("Bearer ", "").strip()
    data = await _xtream_get(creds["username"], creds["password"], "get_vod_info",
                             {"vod_id": stream_id})
    info = data.get("info", {}) if isinstance(data, dict) else {}
    movie_data = data.get("movie_data", {}) if isinstance(data, dict) else {}
    ext = movie_data.get("container_extension") or "mp4"
    return {
        "info": info,
        "movie_data": movie_data,
        "stream_url": _proxy_vod_url(int(stream_id), ext, token),
    }


# ---------------------------------------------------------------------------
# SERIES metadata
# ---------------------------------------------------------------------------
@api_router.get("/series/categories")
async def series_categories(authorization: Optional[str] = Header(None)):
    creds = _get_creds(authorization)
    data = await _xtream_get(creds["username"], creds["password"], "get_series_categories")
    return {"categories": data or []}


@api_router.get("/series/list")
async def series_list(category_id: Optional[str] = None,
                      q: Optional[str] = None,
                      limit: int = 0,
                      authorization: Optional[str] = Header(None)):
    creds = _get_creds(authorization)
    extra = {"category_id": category_id} if category_id and category_id != "ALL" else None
    data = await _xtream_get(creds["username"], creds["password"], "get_series", extra)
    items = data or []
    if q:
        ql = q.lower()
        items = [x for x in items if ql in (x.get("name") or "").lower()]
    total = len(items)
    if limit and limit > 0:
        items = items[:limit]
    return {"series": items, "total": total}


@api_router.get("/series/info/{series_id}")
async def series_info(series_id: str, authorization: Optional[str] = Header(None)):
    creds = _get_creds(authorization)
    token = (authorization or "").replace("Bearer ", "").strip()
    data = await _xtream_get(creds["username"], creds["password"], "get_series_info",
                             {"series_id": series_id})
    seasons = data.get("seasons", []) if isinstance(data, dict) else []
    info = data.get("info", {}) if isinstance(data, dict) else {}
    episodes_raw = data.get("episodes", {}) if isinstance(data, dict) else {}
    episodes: Dict[str, List[Dict[str, Any]]] = {}
    for season_key, ep_list in (episodes_raw or {}).items():
        built = []
        for ep in ep_list or []:
            ext = ep.get("container_extension") or "mp4"
            ep_id = ep.get("id") or ep.get("stream_id")
            built.append({
                "id": ep_id,
                "episode_num": ep.get("episode_num"),
                "season": ep.get("season"),
                "title": ep.get("title") or ep.get("name"),
                "info": ep.get("info", {}),
                "stream_url": _proxy_series_url(int(ep_id), ext, token),
            })
        episodes[str(season_key)] = built
    return {"info": info, "seasons": seasons, "episodes": episodes}


# ---------------------------------------------------------------------------
# STREAM PROXY endpoints
# ---------------------------------------------------------------------------
async def _proxy_bytes(url: str, request: Request, user_agent: str = DEFAULT_UA):
    upstream_headers = {"User-Agent": user_agent}
    r_header = request.headers.get("range")
    if r_header:
        upstream_headers["Range"] = r_header

    client = httpx.AsyncClient(timeout=None, verify=False, follow_redirects=True)
    req = client.build_request("GET", url, headers=upstream_headers)
    try:
        r = await client.send(req, stream=True)
    except Exception as e:
        await client.aclose()
        logger.warning("Proxy connect error for %s: %s", url, e)
        raise HTTPException(status_code=502, detail="Upstream error")

    async def gen():
        try:
            async for chunk in r.aiter_bytes(chunk_size=64 * 1024):
                yield chunk
        except Exception as e:
            logger.info("Proxy stream ended: %s", e)
        finally:
            await r.aclose()
            await client.aclose()

    resp_headers = {}
    for name in ("content-type", "content-length", "content-range", "accept-ranges",
                 "cache-control", "etag", "last-modified"):
        if name in r.headers:
            resp_headers[name.title()] = r.headers[name]
    media_type = r.headers.get("content-type", "application/octet-stream")
    return StreamingResponse(gen(), status_code=r.status_code,
                             headers=resp_headers, media_type=media_type)


async def _proxy_hls_playlist(url: str, token: str, user_agent: str) -> PlainTextResponse:
    async with httpx.AsyncClient(timeout=30, verify=False, follow_redirects=True) as client:
        r = await client.get(url, headers={"User-Agent": user_agent})
        if r.status_code >= 400:
            raise HTTPException(status_code=502, detail="Upstream HLS error")
        body = r.text
        final_url = str(r.url)

    def enc(u: str) -> str:
        return base64.urlsafe_b64encode(u.encode()).decode()

    def resolve(rel_or_abs: str) -> str:
        # Use urljoin against final_url so relative paths, absolute paths,
        # and fully-qualified URLs are all handled correctly. This is the
        # HLS spec behaviour and avoids doubled/collapsed path segments.
        if rel_or_abs.startswith(("http://", "https://")):
            return rel_or_abs
        return urljoin(final_url, rel_or_abs)

    out_lines = []
    for raw in body.splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            if 'URI="' in line:
                def _rw(m: re.Match) -> str:
                    absu = resolve(m.group(1))
                    return (f'URI="/api/stream/seg?u={enc(absu)}'
                            f'&ua={enc(user_agent)}&t={token}"')
                line = re.sub(r'URI="([^"]+)"', _rw, line)
            out_lines.append(line)
            continue
        absu = resolve(line)
        if absu.split("?")[0].endswith(".m3u8"):
            out_lines.append(
                f"/api/stream/hls?u={enc(absu)}&ua={enc(user_agent)}&t={token}"
            )
        else:
            out_lines.append(
                f"/api/stream/seg?u={enc(absu)}&ua={enc(user_agent)}&t={token}"
            )

    return PlainTextResponse(
        "\n".join(out_lines) + "\n",
        media_type="application/vnd.apple.mpegurl",
    )


def _looks_like_hls(url: str, headers: Dict[str, str]) -> bool:
    ct = headers.get("content-type", "").lower()
    return "mpegurl" in ct or url.split("?")[0].endswith(".m3u8")


@api_router.get("/stream/live/{channel_id}")
async def stream_live(channel_id: str, t: str, request: Request):
    creds = _get_creds_from_query(t)
    # Accept both `/stream/live/123` and `/stream/live/123.m3u8` / `.ts` variants
    if channel_id.endswith(".m3u8"):
        channel_id = channel_id[:-5]
    elif channel_id.endswith(".ts"):
        channel_id = channel_id[:-3]
    hls_url = f"{XTREAM_HOST}/live/{creds['username']}/{creds['password']}/{channel_id}.m3u8"
    ts_url = f"{XTREAM_HOST}/live/{creds['username']}/{creds['password']}/{channel_id}.ts"
    ua = DEFAULT_UA

    # Prefer HLS. Detect via HEAD.
    is_hls = True
    upstream_url = hls_url
    try:
        async with httpx.AsyncClient(timeout=10, verify=False, follow_redirects=True) as client:
            head = await client.head(hls_url, headers={"User-Agent": ua})
        if head.status_code >= 400:
            upstream_url = ts_url
            is_hls = False
        else:
            is_hls = _looks_like_hls(hls_url, dict(head.headers))
    except Exception:
        pass

    if is_hls:
        return await _proxy_hls_playlist(upstream_url, t, ua)
    return await _proxy_bytes(upstream_url, request, ua)


@api_router.get("/stream/hls")
async def stream_hls_variant(u: str, t: str, ua: str = ""):
    _get_creds_from_query(t)
    try:
        url = base64.urlsafe_b64decode(u.encode()).decode()
    except Exception:
        raise HTTPException(status_code=400, detail="URL inválida")
    user_agent = base64.urlsafe_b64decode(ua.encode()).decode() if ua else DEFAULT_UA
    return await _proxy_hls_playlist(url, t, user_agent)


@api_router.get("/stream/seg")
async def stream_segment(u: str, t: str, ua: str = "", request: Request = None):
    _get_creds_from_query(t)
    try:
        url = base64.urlsafe_b64decode(u.encode()).decode()
    except Exception:
        raise HTTPException(status_code=400, detail="URL inválida")
    user_agent = base64.urlsafe_b64decode(ua.encode()).decode() if ua else DEFAULT_UA
    return await _proxy_bytes(url, request, user_agent)


@api_router.get("/stream/vod/{stream_id}")
async def stream_vod(stream_id: int, t: str, ext: str, request: Request):
    creds = _get_creds_from_query(t)
    url = f"{XTREAM_HOST}/movie/{creds['username']}/{creds['password']}/{stream_id}.{ext}"
    return await _proxy_bytes(url, request, DEFAULT_UA)


@api_router.get("/stream/series/{ep_id}")
async def stream_series(ep_id: int, t: str, ext: str, request: Request):
    creds = _get_creds_from_query(t)
    url = f"{XTREAM_HOST}/series/{creds['username']}/{creds['password']}/{ep_id}.{ext}"
    return await _proxy_bytes(url, request, DEFAULT_UA)


# ---------------------------------------------------------------------------
# App wiring
# ---------------------------------------------------------------------------
app.include_router(api_router)

app.add_middleware(
    CORSMiddleware,
    allow_credentials=True,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.on_event("startup")
async def _startup():
    logger.info("Nexus backend started (Xtream live mode)")


@app.on_event("shutdown")
async def _shutdown():
    mongo_client.close()
