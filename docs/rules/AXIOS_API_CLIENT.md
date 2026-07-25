# Axios API Client Architecture (Seika Web App)

This document describes the Axios client setup in the web app and how to call Identity-service and UserProfile-service APIs from React.

## 1) Folder Structure

```
src/web-app/src/api/
  adapters.ts
  client.ts
  index.ts
  services/
    auth.ts
    userProfiles.ts
  types.ts
```

## 2) Configuration

- Base URL is read from `VITE_API_BASE_URL`.
- If not provided, it defaults to `/api` and is compatible with the API Gateway routes.

Example `.env` for local dev (web-app root):

```
VITE_API_BASE_URL=http://localhost:8080/api
```

## 3) Auth Token Handling

Use `setAuthToken()` to attach the `Authorization: Bearer <token>` header on the shared Axios instance.

```ts
import { setAuthToken } from "@/api";

setAuthToken(accessToken);
```

To clear it (logout), call:

```ts
setAuthToken(null);
```

## 4) Service Mapping

### Identity-service

- `POST /api/auth/register` -> `authService.register()`
- `POST /api/auth/login` -> `authService.login()`
- `POST /api/auth/refresh` -> `authService.refresh()`
- `GET /api/auth/me` -> `authService.me()`
- `POST /api/auth/jwt-introspect` -> `authService.jwtIntrospect()`

### UserProfile-service

- `POST /api/profiles` -> `userProfilesService.createProfile()`
- `GET /api/profiles` -> `userProfilesService.getAllProfiles()`
- `GET /api/profiles/{userId}` -> `userProfilesService.getProfileByUserId()`

## 5) Usage Examples in React

### Login + save access token

```tsx
import { authService, setAuthToken } from "@/api";

const handleLogin = async () => {
  const data = await authService.login({
    username: "demo",
    password: "secret",
  });

  setAuthToken(data.accessToken);
};
```

### Register + create profile

```tsx
import { authService, userProfilesService, toDateOnlyString } from "@/api";

const handleRegister = async () => {
  const auth = await authService.register({
    username: "student01",
    password: "secret",
    role: "STUDENT",
    fullName: "Student One",
    dateOfBirth: "2005-01-01",
    gender: "FEMALE",
  });

  setAuthToken(auth.accessToken);

  await userProfilesService.createProfile({
    userId: auth.username,
    fullName: "Student One",
    dateOfBirth: toDateOnlyString(new Date("2005-01-01")),
    gender: "FEMALE",
  });
};
```

### Fetch profile by userId

```tsx
import { userProfilesService } from "@/api";

const loadProfile = async (userId: string) => {
  const profile = await userProfilesService.getProfileByUserId(userId);
  return profile;
};
```

## 6) Notes

- `dateOfBirth` is a `LocalDate` on the backend, so the client should send `YYYY-MM-DD` strings.
- This setup avoids adding extra layers; keep service files thin and focused on one microservice.
