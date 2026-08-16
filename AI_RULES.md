# AI Rules & Tech Stack Guidelines

This document outlines the tech stack and development rules for the ZapingX / Nexus IPTV application. All AI agents and developers must adhere to these guidelines when modifying or extending the codebase.

## Tech Stack Overview

*   **Backend Framework:** FastAPI (Python 3.11+) for high-performance, asynchronous API endpoints.
*   **Database Client:** Motor (Async MongoDB driver) for storing user profiles, progress, and settings.
*   **Frontend Framework:** Expo (React Native SDK 54) with TypeScript, supporting iOS, Android, and Web.
*   **Routing:** Expo Router (v6) for file-based, native-feeling navigation.
*   **Video Playback:** `expo-video` for modern, high-performance native video streaming (HLS, VOD, and Live TV).
*   **Image Loading:** `expo-image` for high-performance, cached image rendering with smooth transitions.
*   **Storage:** Custom storage wrapper (`AsyncStorage` for general KV, `expo-secure-store` for sensitive tokens/credentials).
*   **Styling:** Custom theme-based styling (`src/theme.ts`) combined with React Native `StyleSheet` for consistent dark-cinematic UI.

## Library Usage Rules

### 1. Video Playback
*   **Rule:** Always use `expo-video` for video streaming.
*   **Prohibited:** Do not use `expo-av` or any other deprecated video libraries.
*   **Implementation:** Custom controls, picture-in-picture, and fullscreen must be handled via `expo-video` APIs.

### 2. Image Rendering
*   **Rule:** Always use `expo-image` for posters, logos, and covers.
*   **Prohibited:** Do not use the standard React Native `<Image>` component for remote assets.
*   **Reasoning:** `expo-image` provides superior caching, memory management, and placeholder transitions.

### 3. Icons
*   **Rule:** Use `@expo/vector-icons` (specifically `Ionicons`) for all UI icons.
*   **Prohibited:** Do not install or use raw SVG files or other third-party icon packages unless explicitly requested.

### 4. Storage & Session Management
*   **Rule:** Always use the custom storage singleton imported from `@/src/utils/storage`.
*   **Prohibited:** Do not import `AsyncStorage` or `SecureStore` directly in feature screens.
*   **Methods:** Use `storage.getItem`/`storage.setItem` for general data, and `storage.secureGet`/`storage.secureSet` for auth tokens.

### 5. Styling & Theme
*   **Rule:** Adhere to the dark cinematic theme defined in `frontend/src/theme.ts`.
*   **Colors:** Background (`#050505`), Surface (`#14141E`), Primary Accent (`#E50914`).
*   **TV Optimization:** All interactive elements must support explicit focus states using the `FocusCard` component or Pressable's `focused` render prop.

### 6. API Requests
*   **Rule:** Use the pre-configured Axios client from `@/src/api/client`.
*   **Prohibited:** Do not use raw `fetch` or create new Axios instances.