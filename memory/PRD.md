# ZapingX — Product Requirements Document

## Overview
ZapingX is a mobile IPTV streaming app (Expo/React Native) modeled after Zaping TV.
It provides Live TV, Movies (VOD), and Series playback backed by a single Xtream Codes
provider plus a hidden internal M3U playlist.

## User flow
1. User opens app → checks stored token → if none, sees Login screen.
2. Login screen collects `username` + `password` (real credentials of the IPTV provider).
3. Backend `/api/auth/login` validates against Xtream `player_api.php` at
   `https://zone593.com:8443`. On success returns opaque base64 token + user info.
4. Home screen shows featured hero (first movie) + rows: Live Now, Movies, Series.
5. Bottom tabs navigate to Live TV, Movies, Series, Profile.
6. Every list has category chips + search bar.
7. Movie/Series detail screens display cover, metadata, plot and Play button.
8. Series detail lists seasons + episodes with individual play.
9. Player screen uses `expo-video` with native controls, PiP, fullscreen.
10. Profile shows subscription status, expiry, connections and logout.

## Content sources
- **Live TV**: hidden Dropbox M3U (224 legally streamable channels; DRM entries filtered out server-side, source URL never exposed to client).
- **Movies + Series**: Xtream API of `https://zone593.com:8443` using the user's own credentials.

## Non-functional
- Dark cinematic UI, `#050505` background, Netflix red accent `#E50914`.
- Large touch targets, chip rows are horizontal scrollers (never wrap).
- SafeArea aware on every screen and the tab bar.
- Compatible with Android TV/Google TV UI patterns (no D-pad focus glue yet — requires
  a native build with `react-native-tvos` for full TV focus support; Expo Go does not
  run on TV devices).

## Backend endpoints (`/api`)
- `POST /auth/login` – validates credentials, returns `{token, user, server}`.
- `GET /live/categories` – groups derived from hidden M3U.
- `GET /live/channels?category=&q=` – channel list without raw URL.
- `GET /live/play?id=` – returns playable URL for a given channel.
- `GET /vod/categories`, `GET /vod/streams?category_id=&q=`, `GET /vod/info/{id}`.
- `GET /series/categories`, `GET /series/list?category_id=&q=`, `GET /series/info/{id}`.

Everything except `/auth/login` requires `Authorization: Bearer <token>`.
