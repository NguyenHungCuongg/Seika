# Redux Toolkit trong dự án Seika — Hướng dẫn chi tiết

> Tài liệu này giải thích **đầy đủ và chính xác** cách Redux Toolkit hoạt động trong web-app của dự án Seika (`src/web-app/`). Mọi khẳng định đều dựa trên mã nguồn thực tế tại thời điểm viết (commit gần nhất của nhánh `master`).
>
> Đối tượng đọc: thành viên mới onboard frontend, reviewer khi review PR liên quan đến state, hoặc ai muốn hiểu vì sao codebase lại được tổ chức như vậy.
>
> Stack liên quan: **Redux Toolkit 2.x**, **React-Redux 9.x**, **React Router 7**, **Vite 6**, **axios**, **SSE** (cho realtime).

---

## Mục lục

1. [Tổng quan kiến trúc](#1-tổng-quan-kiến-trúc)
2. [Store — `src/store/index.ts`](#2-store--srcstoreindexts)
3. [Typed Hooks — `src/store/hooks.ts`](#3-typed-hooks--srcstorehooksts)
4. [Các slice — `src/store/*.ts`](#4-các-slice--srcstorets)
   - 4.1 [`authSlice` — xác thực & persistence](#41-authslice--xác-thực--persistence)
   - 4.2 [`userProfileSlice` — hồ sơ người dùng](#42-userprofileslice--hồ-sơ-người-dùng)
   - 4.3 [`notificationSlice` — thông báo](#43-notificationslice--thông-báo)
   - 4.4 [`statisticsSlice` — dashboard giáo viên](#44-statisticsslice--dashboard-giáo-viên)
   - 4.5 [`adminSlice` — dashboard quản trị](#45-adminslice--dashboard-quản-trị)
5. [Tầng API — `src/api/`](#5-tầng-api--srapi)
   - 5.1 [`client.ts` — axios + 401 auto-refresh](#51-clientts--axios--401-auto-refresh)
   - 5.2 [`errors.ts`, `adapters.ts`, `tokenUtils.ts`](#52-errorsts-adaptersts-tokenutilsts)
   - 5.3 [Các service theo domain](#53-các-service-theo-domain)
6. [Luồng Auth đầu cuối](#6-luồng-auth-đầu-cuối)
7. [Realtime với SSE](#7-realtime-với-sse)
8. [Quy tắc & best-practice đã chốt](#8-quy-tắc--best-practice-đã-chốt)
9. [Bảng tra cứu nhanh](#9-bảng-tra-cứu-nhanh)

---

## 1. Tổng quan kiến trúc

Redux Toolkit trong Seika được tổ chức theo mô hình **"một slice cho một domain"** (mỗi slice phụ trách một lĩnh vực nghiệp vụ). Tất cả tương tác với store đều đi qua:

```
              ┌─────────────────────────────┐
              │       React Component       │
              │  (Login, Dashboard, …)      │
              └─────────┬───────────┬───────┘
                        │           │
            useAppSelector      useAppDispatch
                        ▼           ▼
              ┌─────────────────────────────┐
              │    Store (configureStore)   │
              │  ┌───────┬───────┬───────┐  │
              │  │ auth  │ user  │ notif │  │
              │  │ slice │profile│ slice │  │
              │  └───────┴───────┴───────┘  │
              └─────────────┬───────────────┘
                            │
                   createAsyncThunk
                            │
                            ▼
              ┌─────────────────────────────┐
              │   src/api/services/*.ts     │
              │ (authService, wallet...)    │
              └─────────────┬───────────────┘
                            │ axios + interceptor
                            ▼
              ┌─────────────────────────────┐
              │   Backend (Spring Boot API) │
              └─────────────────────────────┘
```

**Đặc điểm then chốt:**

- **Không dùng `redux-persist`**. Auth persistence được làm thủ công (xem [§4.1](#41-authslice--xác-thực--persistence)) trong `authSlice.ts` + `localStorage`/`sessionStorage`.
- **Không dùng RTK Query**. Mọi gọi API đều thông qua `createAsyncThunk` kết hợp `src/api/services/`.
- **Không cấu hình middleware thủ công** — `configureStore` mặc định đã bật `thunk`, `serializableCheck`, `immutableCheck`.
- **Có 5 slice** đang hoạt động: `auth`, `userProfile`, `notifications`, `statistics`, `admin`.
- **Phạm vi persisted state**: chỉ mỗi `auth`. Các slice khác tự load lại mỗi lần mount trang.

---

## 2. Store — `src/store/index.ts`

`src/store/index.ts` rất ngắn gọn — chỉ `configureStore` và xuất type.

```ts
// src/store/index.ts
import { configureStore } from "@reduxjs/toolkit";
import authReducer from "./authSlice";
import userProfileReducer from "./userProfileSlice";
import notificationReducer from "./notificationSlice";
import statisticsReducer from "./statisticsSlice";
import adminReducer from "./adminSlice";

export const store = configureStore({
  reducer: {
    auth: authReducer,
    userProfile: userProfileReducer,
    notifications: notificationReducer,
    statistics: statisticsReducer,
    admin: adminReducer,
  },
});

export type AppStore = typeof store;
export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
```

**Ba type được suy ra tự động:**

| Type          | Vai trò                                                                      |
| ------------- | ---------------------------------------------------------------------------- |
| `RootState`   | Toàn bộ state shape của store. Dùng để typed `useSelector`.                  |
| `AppDispatch` | Dispatch đã được mở rộng bởi thunk middleware. Dùng để typed `useDispatch`.  |
| `AppStore`    | Kiểu của chính đối tượng `store`. Ít dùng trong component, chủ yếu cho test. |

**Tại sao lại không có middleware tuỳ biếnh?** Vì:

- `createAsyncThunk` đã hoạt động mặc định — không cần `redux-thunk` cài thêm.
- Không có side-effect ngoài Redux (ví dụ logging, persist) để bọc.
- Mọi side-effect phức tạp (auto-refresh token) được đặt trong **axios interceptor** ở `src/api/client.ts`, không nằm trong store.

---

## 3. Typed Hooks — `src/store/hooks.ts`

`src/store/hooks.ts` đóng gói `useDispatch`/`useSelector` với type đã suy ra từ store. Quy tắc bắt buộc của dự án: **không bao giờ** import trực tiếp `useDispatch`/`useSelector` từ `react-redux`.

```ts
// src/store/hooks.ts
import {
  useDispatch,
  useSelector,
  type TypedUseSelectorHook,
} from "react-redux";
import type { AppDispatch, RootState } from "./index";

export const useAppDispatch = () => useDispatch<AppDispatch>();
export const useAppSelector: TypedUseSelectorHook<RootState> = useSelector;
```

**Lý do:**

- `useAppDispatch()` trả về dispatch đã biết về thunk → gọi được `dispatch(login({…}))` mà TS không complain.
- `useAppSelector(state => state.x.y)` có auto-complete đầy đủ cho `state`, bắt lỗi truy cập field không tồn tại ngay lúc viết.

Mọi ví dụ trong tài liệu này đều dùng hai hook trên.

---

## 4. Các slice — `src/store/*.ts`

Mỗi slice được cấu trúc theo một khuôn mẫu giống nhau:

```text
createSlice({
  name: "<domain>",
  initialState,
  reducers: {  /* sync actions */ },
  extraReducers: builder => {
    builder
      .addCase(thunk.pending, ...)
      .addCase(thunk.fulfilled, ...)
      .addCase(thunk.rejected, ...);
  },
});

export const { <syncAction1>, ... } = slice.actions;
export const <asyncThunk> = createAsyncThunk(...);
export default slice.reducer;
```

Tổng cộng có **28 async thunk**, **12 action đồng bộ** rải đều qua 5 slice. Chi tiết từng slice dưới đây.

---

### 4.1 `authSlice` — xác thực & persistence

File: `src/store/authSlice.ts`. Slice phức tạp nhất vì nó vừa quản lý state thuần, vừa tự lo persistence + tương tác với axios interceptor.

#### 4.1.1 State shape

```ts
type AuthStorageState = {
  accessToken: string | null;
  refreshToken: string | null;
  tokenType: string | null;
  username: string | null;
  roles: string[];
};

type AuthState = AuthStorageState & {
  status: "idle" | "loading" | "succeeded" | "failed";
  error: string | null;
};
```

Các trường "storage" (`accessToken`, `refreshToken`, `username`, `roles`, `tokenType`) là những gì sẽ được **lưu xuống bộ nhớ ngoài** (local/session storage). Hai trường `status` và `error` chỉ tồn tại in-memory.

#### 4.1.2 Persistence — cách Seika "tự chế" redux-persist

Không dùng thư viện. Persistence hoàn toàn viết tay theo các bước:

```ts
// src/store/authSlice.ts (rút gọn)
const STORAGE_KEY = "seika.auth";

function getStoredAuth(): AuthStorageState | null {
  // Đọc localStorage trước, rơi xuống sessionStorage
  // Nếu accessToken hết hạn:
  //   - có refreshToken → giữ, để interceptor xử lý 401
  //   - không có        → xoá cả hai storage
}

const initialState: AuthState = {
  ...(getStoredAuth() ?? emptyAuthStorageState),
  status: "idle",
  error: null,
};
```

**`initialState` được tính NGAY khi module được import**, trước cả khi React mount. Đây là điểm quan trọng:

- Nếu user đã đăng nhập từ phiên trước, reload trang → `import authSlice` → `initialState` đã có token → `setAuthToken(stored.accessToken)` được gọi ngay tại đây → axios instance đã sẵn sàng gắn header cho request đầu tiên.
- Không cần vòng `useEffect` phức tạp để "boot" auth.

**Quy tắc lưu trữ:**

| `rememberMe`        | Nơi lưu                                                 |
| ------------------- | ------------------------------------------------------- |
| `true` (khi login)  | `localStorage` (sống qua các tab / đóng mở trình duyệt) |
| `false` (khi login) | `sessionStorage` (mất khi đóng tab)                     |

Mỗi lần ghi đều **xoá storage còn lại** để tránh hai bản song song.

#### 4.1.3 Async thunks

| Thunk      | Tham số                                              | Quy trình                                                                                                                                    |
| ---------- | ---------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------- |
| `login`    | `{ credentials: LoginRequest; rememberMe: boolean }` | Gọi `authService.login` → set axios header → `persistAuth(auth, rememberMe)`. On reject trả về `getApiErrorMessage(error, "Login failed.")`. |
| `register` | `RegisterRequest`                                    | Tương tự `login` nhưng luôn `persistAuth(auth, true)`.                                                                                       |

Cả hai đều khai báo `{ rejectValue: string }` để `unwrap()` hoặc `state.auth.error` trả về chuỗi thân thiện (tiếng Việt fallback).

#### 4.1.4 Sync reducers

| Action                    | Công dụng                                                                                                                                                                                |
| ------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `logout`                  | Đặt state về `emptyAuthStorageState`, `setAuthToken(null)`, `clearPersistedAuth()`.                                                                                                      |
| `clearAuthError`          | Đặt `error = null`, dùng khi user muốn thử lại sau lỗi.                                                                                                                                  |
| `setCredentials(payload)` | Overwrite state từ payload; set axios header; `persistAuth(..., true)`. Được **axios interceptor** gọi khi refresh token thành công (xem [§5.1](#51-clientts--axios--401-auto-refresh)). |

#### 4.1.5 extraReducers

Lắng nghe ba lifecycle của `login` và `register` để cập nhật `status` / `error` / copy payload vào state.

---

### 4.2 `userProfileSlice` — hồ sơ người dùng

File: `src/store/userProfileSlice.ts`.

#### 4.2.1 State shape

Chia rõ theo hai nguồn backend:

```ts
{
  // Identity (từ /auth/me)
  userId, username, roles,

  // Profile (từ /profiles/{userId})
  profileId, fullName, dateOfBirth, gender,
  profilePictureUrl, exp, level,
  currentStreak, longestStreak, quizzesCompleted,

  // UI
  status: "idle" | "loading" | "succeeded" | "failed",
  error
}
```

#### 4.2.2 Thunk `fetchCurrentUserProfile`

```ts
// Pseudocode
createAsyncThunk(
  "userProfile/fetchCurrentUserProfile",
  async (_, { rejectWithValue }) => {
    try {
      const identity = await authService.me(); // /auth/me
      const profile = await userProfilesService.getByUserId(identity.id); // /profiles/{id}
      return { identity, profile };
    } catch (e) {
      return rejectWithValue(getApiErrorMessage(e, "Không tải được hồ sơ."));
    }
  },
);
```

Trên `fulfilled`, cả hai phần được "trải" xuống state.

#### 4.2.3 Reset tự động khi đổi user

`extraReducers` lắng nghe cả ba action **bằng chuỗi type** của auth:

```ts
builder
  .addCase(login.fulfilled, () => initialState)
  .addCase(register.fulfilled, () => initialState)
  .addMatcher(
    (action) => action.type === "auth/setCredentials",
    () => initialState,
  );
```

Mục đích: khi user A logout → user B login, profile cũ của A phải bị xoá ngay — không cần layout phải `dispatch(clearUserProfile)` thủ công.

#### 4.2.4 Sync reducer

`clearUserProfile()` trả về `initialState`, dùng trong `StudentDashboardLayout.handleLogout`.

---

### 4.3 `notificationSlice` — thông báo

File: `src/store/notificationSlice.ts`.

#### 4.3.1 State

```ts
{
  items: NotificationResponse[],
  unreadCount: number,
  status, error
}
```

#### 4.3.2 Bốn thunk

| Thunk                | Endpoint                                               | Công dụng                                                |
| -------------------- | ------------------------------------------------------ | -------------------------------------------------------- |
| `fetchNotifications` | `GET /notifications/me?page=&size=` (size mặc định 50) | Load danh sách + tính lại `unreadCount` từ items.        |
| `fetchUnreadCount`   | `GET /notifications/me/unread-count`                   | Cập nhật số badge nhanh.                                 |
| `markAsRead(id)`     | `PATCH /notifications/{id}/read`                       | Lật `status: UNREAD → READ`, giảm `unreadCount` (sàn 0). |
| `markAllAsRead()`    | `PATCH /notifications/me/read-all`                     | Lật tất cả, đặt `unreadCount = 0`.                       |

#### 4.3.3 Sync reducer `addNotification(payload)` — điểm vào của SSE

Reducer này **KHÔNG** được dispatch bởi component — nó là điểm cuối của luồng SSE realtime:

```text
notification-service (SSE)
        │  event: NotificationResponse
        ▼
useNotificationSSE hook (parse JSON)
        │  dispatch(addNotification(data))
        ▼
notificationSlice.addNotification → prepend vào items, ++unreadCount nếu UNREAD
```

Reducer này rất quan trọng vì nó giúp badge unread cập nhật **ngay khi** backend đẩy event, không cần chờ user F5.

Xem chi tiết SSE trong [§7](#7-realtime-với-sse).

---

### 4.4 `statisticsSlice` — dashboard giáo viên

File: `src/store/statisticsSlice.ts`.

#### 4.4.1 State — một slice, nhiều "vùng"

```ts
{
  overviewStatus, overviewError,         // fetchStatisticsOverview
  quizOverview, flashcardOverview, revenue,

  topProducts, topProductsStatus, topProductsError,   // fetchTopProducts
  students,    studentsStatus,    studentsError,        // fetchStudents

  attemptsByQuizSet,                       // map[quizSetId → QuizAttempt[]]
  attemptsStatus, attemptsError,
}
```

Một slice nhưng **nhiều cụm status/error** vì dashboard teacher render nhiều panel độc lập. Nếu dùng chung một `status`, lỗi một panel sẽ tắt cả spinner còn lại.

#### 4.4.2 Năm thunk

| Thunk                                        | Mục đích                                                                                                                                      |
| -------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------- |
| `fetchStatisticsOverview`                    | `statisticsService.fetchOverviewBundle()` — Promise.all 3 request parallel, có `.catch(() => null)` để một endpoint lỗi không kéo sập cả cụm. |
| `fetchRevenue("month" \| "day")`             | Biểu đồ doanh thu.                                                                                                                            |
| `fetchTopProducts({ productType?, limit? })` | Bảng top sản phẩm (giới hạn 10 mặc định).                                                                                                     |
| `fetchStudents(limit?)`                      | Danh sách học viên.                                                                                                                           |
| `fetchQuizAttempts(quizSetId)`               | Drill-down cho 1 quiz, lưu vào `attemptsByQuizSet[quizSetId]` để mở modal.                                                                    |

#### 4.4.3 `clearStatistics()` reducer

Đặt về `initialState` — dự phòng để dùng khi teacher đổi tài khoản.

---

### 4.5 `adminSlice` — dashboard quản trị

File: `src/store/adminSlice.ts`. Slice lớn nhất, **12 thunk**, phục vụ admin.

#### 4.5.1 State — bốn "vùng" con

```ts
{
  dashboard: AdminDashboardStats | null,        // fetchAdminDashboard
  dashboardStatus, dashboardError,

  users:  { content: UserAdminResponse[], page, size, totalElements, totalPages,
            status, error, filterRole },        // fetchAdminUsers + lock/unlock/role/reset

  products: { content: PendingProduct[], ... },  // fetchPendingProducts + approve/reject

  configs: SystemConfigEntry[], configsStatus, configsError,   // fetchAdminConfigs + updateAdminConfig

  // Cờ cho "thao tác ghi" gần nhất (lock/unlock/role/reset/approve/reject/updateConfig)
  mutationStatus, mutationError,
}
```

#### 4.5.2 Mười hai thunk

Đọc (5):

1. `fetchAdminDashboard` → `GET /admin/dashboard/stats`
2. `fetchAdminUsers({ role?, page?, size? })` → Spring `Page`
3. `fetchPendingProducts({ page?, size? })`
4. `fetchAdminConfigs` → `GET /wallet/admin/configs`
5. (đã liệt kê 5 với số 1–4; thực tế còn `fetch…` lẻ — xem tổng hợp ở [§9](#9-bảng-tra-cứu-nhanh))

Viết (7):

6. `lockAdminUser(userId)`
7. `unlockAdminUser(userId)`
8. `changeAdminUserRole({ userId, role: 'STUDENT' | 'TEACHER' })`
9. `resetAdminUserPassword(userId)`
10. `approveAdminProduct(productId)`
11. `rejectAdminProduct({ productId, reason })`
12. `updateAdminConfig({ key, value })`

#### 4.5.3 `extraReducers` — tinh hoa của slice này

**Helper `replaceUser(state, updated)`** được dùng bởi 3 thunk (`lock`, `unlock`, `changeRole`) để thay thế user trong `state.users.content` mà không cần reload danh sách.

**Approve/Reject product**: filter sản phẩm khỏi `state.products.content` ngay khi `fulfilled`, đồng thời `--totalElements` (sàn 0) để pagination không lệch.

**`updateAdminConfig`**: thay thế theo `key` nếu đã có, ngược lại `push` mới.

**`addMatcher` — cờ `mutationStatus`**:

```ts
const WRITE_THUNKS = [
  lockAdminUser,
  unlockAdminUser,
  changeAdminUserRole,
  resetAdminUserPassword,
  approveAdminProduct,
  rejectAdminProduct,
  updateAdminConfig,
];

builder.addMatcher(
  (action) =>
    WRITE_THUNKS.some((t) => t.rejected.match(action)) ||
    WRITE_THUNKS.some((t) => t.fulfilled.match(action)),
  (state, action) => {
    state.mutationStatus = action.type.endsWith("/fulfilled")
      ? "succeeded"
      : "failed";
    state.mutationError = action.type.endsWith("/rejected")
      ? (action.payload ?? "Unknown")
      : null;
  },
);
```

Kết quả: bất kỳ thao tác ghi nào fail, UI chỉ cần select `state.admin.mutationStatus`/`mutationError` để bật toast — không cần biết thao tác nào vừa chạy.

#### 4.5.4 Sync reducers

`setUsersRoleFilter(role)`, `setUsersPage(page)`, `setUsersSize(size)`, `setProductsPage`, `setProductsSize`, `clearMutationStatus()` — điều khiển filter và tắt cờ mutation sau khi UI đã đọc xong.

---

## 5. Tầng API — `src/api/`

Cấu trúc thư mục theo tài liệu `src/web-app/API_ARCHITECTURE_&_GUIDELINES.md`:

```text
src/api/
├── client.ts               # axios instance + 401 refresh interceptor
├── errors.ts               # getApiErrorMessage (chuỗi lỗi thân thiện)
├── adapters.ts             # (đang trống — dành cho snake_case → camelCase)
├── tokenUtils.ts           # decode JWT, isTokenExpired
├── types.ts                # ApiResponse<T>, UserProfileResponse, …
├── index.ts                # barrel re-export
└── services/
    ├── auth.ts
    ├── userProfiles.ts
    ├── flashcards.ts
    ├── quizzes.ts
    ├── wallet.ts
    ├── marketplace.ts
    ├── notifications.ts
    ├── rewards.ts
    ├── teacherProfile.ts
    ├── statistics.ts
    ├── admin.ts
    └── index.ts            # barrel
```

Quy tắc sống còn: **UI chỉ import từ `@/api`** (barrel), không configure axios thủ công ở component.

---

### 5.1 `client.ts` — axios + 401 auto-refresh

File: `src/api/client.ts`. Đây là file **phức tạp nhất** ở tầng API.

#### 5.1.1 Khởi tạo

```ts
const baseURL =
  import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080/api";
export const apiClient = axios.create({ baseURL, timeout: 15000 });
```

#### 5.1.2 `setAuthToken(token | null)` — setter thuần

```ts
export function setAuthToken(token: string | null) {
  if (token)
    apiClient.defaults.headers.common.Authorization = `Bearer ${token}`;
  else delete apiClient.defaults.headers.common.Authorization;
}
```

Được gọi từ 3 chỗ:

1. Trong `authSlice.ts` lúc module-load (khi tìm thấy token đã lưu).
2. Sau khi `login.fulfilled` / `register.fulfilled`.
3. Sau khi `refresh-token` thành công (trong interceptor).
4. Trong `logout` reducer.

#### 5.1.3 `setupAuthInterceptor(...)` — bẻ gãy vòng tròn import

`client.ts` cần dispatch action, nhưng cũng được import từ `authSlice.ts`. Import trực tiếp sẽ gây **circular import**. Dự án giải quyết bằng dependency injection thủ công:

```ts
// src/api/client.ts (rút gọn)
let dispatchRef: ((action: unknown) => void) | null = null;
let logoutActionCreator: (() => unknown) | null = null;
let setCredentialsActionCreator: ((payload: unknown) => unknown) | null = null;

export const setupAuthInterceptor = (injection: {
  dispatch: (action: unknown) => void;
  logout: () => unknown;
  setCredentials: (payload: unknown) => unknown;
}) => {
  dispatchRef = injection.dispatch;
  logoutActionCreator = injection.logout;
  setCredentialsActionCreator = injection.setCredentials;
};
```

`src/main.tsx` gọi ngay trước khi render:

```tsx
// src/main.tsx (rút gọn)
import { setupAuthInterceptor } from "./api/client";
import { logout, setCredentials } from "./store/authSlice";

setupAuthInterceptor({
  dispatch: store.dispatch,
  logout,
  setCredentials,
});

createRoot(rootEl).render(
  <StrictMode>
    <Provider store={store}>
      <App />
    </Provider>
  </StrictMode>,
);
```

> Thứ tự này quan trọng: **setup phải chạy trước khi component nào đó gây ra request đầu tiên**, nếu không interceptor có thể thiếu dispatch.

#### 5.1.4 Response interceptor — flow auto-refresh token

Đây là "trái tim" của phần auth. Đọc kỹ:

```ts
apiClient.interceptors.response.use(
  (r) => r,
  async (error) => {
    const originalRequest = error.config as any;
    if (error.response?.status !== 401 || originalRequest._retry)
      return Promise.reject(error);

    const refreshToken = getStoredRefreshToken(); // đọc thẳng localStorage
    if (!refreshToken) {
      forceLogout();
      return Promise.reject(error);
    }

    // === Có nhiều request lỗi 401 cùng lúc ===
    if (isRefreshing) {
      return new Promise((resolve, reject) => {
        failedQueue.push({
          resolve: (token) => {
            originalRequest.headers.Authorization = `Bearer ${token}`;
            resolve(apiClient(originalRequest)); // retry
          },
          reject: (err) => reject(err),
        });
      });
    }

    originalRequest._retry = true;
    isRefreshing = true;
    try {
      const { data } = await axios.post(`${baseURL}/auth/refresh`, {
        refreshToken,
      });
      // ^^^ dùng raw axios.post, KHÔNG dùng apiClient, để không re-trigger interceptor

      const newToken = data.accessToken;
      updateStoredAuth(data); // ghi lại storage
      setAuthToken(newToken); // cập nhật header
      dispatchRef!(setCredentialsActionCreator!(data)); // cập nhật Redux
      processQueue(null, newToken); // resolve các request đang chờ
      originalRequest.headers.Authorization = `Bearer ${newToken}`;
      return apiClient(originalRequest); // retry request gốc
    } catch (refreshErr) {
      processQueue(refreshErr, null); // reject mọi request chờ
      forceLogout(); // xoá token, redirect /auth/login
      return Promise.reject(refreshErr);
    } finally {
      isRefreshing = false;
    }
  },
);
```

**Hai cơ chế chống loop vô tận:**

1. `_retry` flag — đánh dấu request đã được thử lại; nếu nó lại trả 401 thì **không** thử lại lần nữa.
2. Raw `axios.post` cho lệnh refresh — đi ra ngoài `apiClient` nên không bị chính interceptor bắt lại.

**Hai cơ chế chống "stampede":**

1. `isRefreshing` boolean + `failedQueue` array — mọi request 401 phát sinh trong lúc đang refresh đều **xếp hàng**, và được `processQueue` giải quyết ngay khi refresh xong (success hay fail).
2. Chỉ **một** request `/auth/refresh` được bay ra ngoài broker dù có N request cùng lúc fail.

**`forceLogout()`** làm gì?

```ts
function forceLogout() {
  removeStoredAuth(); // xoá "seika.auth" ở cả hai storage
  setAuthToken(null);
  if (dispatchRef && logoutActionCreator) dispatchRef(logoutActionCreator());
  const path = window.location.pathname;
  if (!path.startsWith("/auth") && path !== "/home" && path !== "/") {
    window.location.href = "/auth/login";
  }
}
```

Đây là lý do vì sao khi refresh token hết hạn, người dùng tự động bị đẩy về `/auth/login` mà không phải nhấn nút Logout.

---

### 5.2 `errors.ts`, `adapters.ts`, `tokenUtils.ts`

#### `errors.ts`

```ts
import { isAxiosError } from "axios";

export const getApiErrorMessage = (
  error: unknown,
  fallback = "Something went wrong…",
) => {
  if (
    isAxiosError<{ message?: string; error?: string; detail?: string }>(error)
  ) {
    const p = error.response?.data;
    return p?.message ?? p?.error ?? p?.detail ?? fallback;
  }
  if (error instanceof Error) return error.message;
  return fallback;
};
```

- `payload.message ?? payload.error ?? payload.detail ?? fallback` — khớp với `ApiResponse<T>` (server trả về `{code, message, data, timestamp}`) **và** với REST exception trả về `{error, detail}`.
- Mọi thunk dùng `{ rejectValue: string }` đều wrap lỗi qua hàm này, kèm fallback tiếng Việt ngữ cảnh ("Đăng nhập thất bại.", "Không tải được thông báo.", …).

#### `adapters.ts`

Hiện đang rất sơ khai — chỉ một hàm `toDateOnlyString(value: Date) => "YYYY-MM-DD"`. Được dành chỗ cho các transform snake*case → camelCase khi backend thay đổi naming, theo tinh thần tài liệu `API_ARCHITECTURE*&\_GUIDELINES.md`.

#### `tokenUtils.ts`

```ts
decodeJwtPayload(token): Record<string, unknown> | null {
  // Tách phần payload (giữa), base64url-decode, trả về object hoặc null.
}

isTokenExpired(token, bufferSeconds = 30): boolean {
  // Đọc payload.exp (epoch giây); true nếu exp - buffer ≤ now (ms).
}
```

`bufferSeconds = 30` — coi như sắp hết hạn trước 30s để client kịp cảnh báo / refresh chủ động.

---

### 5.3 Các service theo domain

Mỗi service là một object phẳng (`authService = { login, register, … }`). Quy tắc: service chỉ gọi HTTP, **không** xử lý loading/toast/redirect.

#### Tổng hợp endpoint

| Service                                                     | Endpoint                                                                                                                                                                                                        | HTTP                | Ghi chú                                                               |
| ----------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------- | --------------------------------------------------------------------- | ---- | ----------------------------------- |
| `authService.register`                                      | `/auth/register`                                                                                                                                                                                                | POST                | Trả `AuthResponse`                                                    |
| `authService.login`                                         | `/auth/login`                                                                                                                                                                                                   | POST                |                                                                       |
| `authService.refresh`                                       | `/auth/refresh`                                                                                                                                                                                                 | POST                | Dùng raw axios (xem §5.1.4)                                           |
| `authService.jwtIntrospect`                                 | `/auth/jwt-introspect`                                                                                                                                                                                          | POST                |                                                                       |
| `authService.me`                                            | `/auth/me`                                                                                                                                                                                                      | GET                 |                                                                       |
| `userProfilesService.getByUserId(id)`                       | `/profiles/{id}`                                                                                                                                                                                                | GET                 | `encodeURIComponent`                                                  |
| `flashcardsService.*`                                       | `/flashcards` + sub-paths                                                                                                                                                                                       | POST/GET/PUT/DELETE | `/flashcards/buy`, `/complete`, `/search?key=…`, `/author/{userId}`   |
| `quizzesService.*`                                          | `/quiz` + `/quiz-sets`                                                                                                                                                                                          | mọi verb            | Trả `ApiResponse<T>` (giữ nguyên envelope)                            |
| `walletService.getBalance`                                  | `/wallet/balance`                                                                                                                                                                                               | GET                 | Defensive normalize về `{balance: number}` vì backend chưa chốt shape |
| `walletService.getHistory`                                  | `/wallet/history`                                                                                                                                                                                               | POST                |                                                                       |
| `walletService.withdraw / deposit / cashOut / topUp`        | `/wallet/{verb}`                                                                                                                                                                                                | POST                |                                                                       |
| `walletService.getConfigs`                                  | `/wallet/configs`                                                                                                                                                                                               | GET                 | Admin dùng                                                            |
| `marketplaceApi.listProducts`                               | `/marketplace/products`                                                                                                                                                                                         | GET                 |                                                                       |
| `marketplaceApi.listMyProducts`                             | `/marketplace/products/my-products`                                                                                                                                                                             | GET                 |                                                                       |
| `marketplaceApi.listInventory`                              | `/marketplace/inventory/my-items`                                                                                                                                                                               | GET                 |                                                                       |
| `marketplaceApi.createOrder`                                | `/marketplace/orders`                                                                                                                                                                                           | POST                | Body `{userId, items: OrderItemRequest[]}`                            |
| `notificationsService.getAll`                               | `/notifications/me?page=&size=`                                                                                                                                                                                 | GET                 | Default page 0, size 10                                               |
| `notificationsService.getUnreadCount`                       | `/notifications/me/unread-count`                                                                                                                                                                                | GET                 |                                                                       |
| `notificationsService.markAsRead(id)`                       | `/notifications/{id}/read`                                                                                                                                                                                      | PATCH               |                                                                       |
| `notificationsService.markAllAsRead`                        | `/notifications/me/read-all`                                                                                                                                                                                    | PATCH               |                                                                       |
| `rewardsService.getStatus`                                  | `/rewards/status?type=&itemId=`                                                                                                                                                                                 | GET                 |                                                                       |
| `teacherProfileService.getMine / getById`                   | `/profiles/teacher/me` + `/profiles/teacher/{userId}`                                                                                                                                                           | GET                 |                                                                       |
| `statisticsService.*`                                       | `/quiz-sets/my/statistics`, `/flashcards/my/statistics`, `/marketplace/orders/seller/me/revenue?period=`, `/top-products`, `/students`, `/top-selling`, `/flashcards/{id}/students`, `/quiz-sets/{id}/attempts` | GET                 | `fetchOverviewBundle()` = Promise.all 3 endpoint                      |
| `adminService.getDashboardStats`                            | `/admin/dashboard/stats`                                                                                                                                                                                        | GET                 |                                                                       |
| `adminService.listUsers(role, page, size)`                  | `/admin/users`                                                                                                                                                                                                  | GET                 | Trả Spring `Page`, unwrap thủ công                                    |
| `adminService.lockUser` / `unlockUser`                      | `/admin/users/{id}/lock                                                                                                                                                                                         | unlock`             | POST                                                                  |      |
| `adminService.changeUserRole({id, role})`                   | `/admin/users/{id}/role`                                                                                                                                                                                        | PUT                 |                                                                       |
| `adminService.resetUserPassword`                            | `/admin/users/{id}/reset-password`                                                                                                                                                                              | POST                |                                                                       |
| `adminService.listPendingProducts(page, size)`              | `/marketplace/admin/products/pending`                                                                                                                                                                           | GET                 |                                                                       |
| `adminService.approveProduct / rejectProduct / hideProduct` | `/marketplace/admin/products/{id}/approve                                                                                                                                                                       | reject              | hide`                                                                 | POST | `rejectProduct` cần body `{reason}` |
| `adminService.listConfigs / updateConfig(key, {value})`     | `/wallet/admin/configs` (+ `/{key}` cho PUT)                                                                                                                                                                    | GET / PUT           |                                                                       |
| `adminService.getRevenueStats / listTransactions`           | `/wallet/admin/revenue-stats`, `/wallet/admin/transactions?type=`                                                                                                                                               | GET                 |                                                                       |

> **Đặc điểm không đồng nhất**:
>
> - `quizzes.ts` giữ nguyên envelope `ApiResponse<T>` (UI phải tự `.data`).
> - `admin.ts`, `statistics.ts` dùng helper `unwrap<T>` để "bóc" `.data` nếu có, ngược lại trả nguyên payload.
> - `wallet.ts` còn có một số method trả `any` — xem như "đang chờ chốt shape".
>
> Khi thêm service mới, hãy chọn **một** style (unwrap sẵn hoặc giữ envelope) cho cả file rồi ghi chú vào PR.

---

## 6. Luồng Auth đầu cuối

### 6.1 Login

```text
1. User nhập form trong Login.tsx
2. dispatch(login({ credentials: { username, password }, rememberMe }))
        │
        ▼ (slice xử lý)
3. login.pending → state.auth.status = "loading"
4. Gọi authService.login → axios POST /auth/login
5. Nhận AuthResponse {accessToken, refreshToken, tokenType, username, roles}
        │
        ▼
6. setAuthToken(accessToken)         → axios gắn header "Authorization: Bearer …"
7. persistAuth(auth, rememberMe)     → ghi "seika.auth" vào localStorage hoặc sessionStorage
8. fulfilled → copy payload vào state.auth, status = "succeeded"
        │
        ▼
9. Component dùng state.auth.roles để điều hướng:
   - có ROLE_ADMIN    → /admin/dashboard
   - có TEACHER       → /teacher/dashboard
   - ngược lại        → /student/dashboard
```

### 6.2 App boot — rehydrate

```text
1. import authSlice          → initialState được tính NGAY tại đây
2. getStoredAuth():
     - đọc "seika.auth" từ localStorage (ưu tiên), rơi xuống sessionStorage
     - nếu accessToken hết hạn:
         * có refreshToken → giữ (sẽ nhờ interceptor)
         * không có         → xoá cả hai
3. setAuthToken(stored.accessToken)  → axios đã có header ngay từ request đầu
4. <Provider> mount, App render
5. Layout (Student/TeacherDashboardLayout) gọi useEffect:
     - nếu state.auth đã có username → thử fetchCurrentUserProfile()
     - gọi fetchNotifications()
     - mở SSE stream
```

### 6.3 Auto-refresh khi gặp 401

```text
Bất kỳ request nào dùng apiClient lại trả 401
        │
        ▼
interceptor (client.ts):
  read refreshToken từ "seika.auth"
        │
        ├── không có → forceLogout() → đẩy về /auth/login
        │
        ▼
   axios POST /auth/refresh            (raw axios, bypass interceptor)
        │
        ├── lỗi → processQueue(err, null), forceLogout()
        │
        ▼
   thành công:
     updateStoredAuth(...)
     setAuthToken(newToken)
     dispatchRef(setCredentialsActionCreator(data))   ← mirror vào Redux
     processQueue(null, newToken)                     ← giải phóng các request đang chờ
     retry(originalRequest)
```

### 6.4 Logout

Có hai dạng:

- **User chủ động** (bấm nút trong layout):
  ```ts
  const handleLogout = () => {
    dispatch(logout()); // authSlice: xoá state + storage, setAuthToken(null)
    dispatch(clearUserProfile()); // userProfileSlice: reset
    navigate("/auth/login");
  };
  ```
- **Bị động** (refresh fail, 401 không cứu được): `forceLogout()` trong interceptor làm điều tương tự + `window.location.href`.

> Hiện tại `clearNotifications()` và `clearStatistics()` **không** tự động chạy theo logout. Nếu một người dùng khác đăng nhập lại trên cùng tab và mở dashboard admin, có thể thấy dữ liệu cũ chưa kịp refetch. Khi thấy bug này trong tương lai, dispatch thêm các action đó trong `authSlice.logout` (cẩn thận circular import — chỉ dispatch bằng `dispatchRef` chứ không import slice trực tiếp).

---

## 7. Realtime với SSE

`notification-service` duy trì kết nối Server-Sent Events (`SseService` giữ `Map<userId, SseEmitter>`).

File frontend: `src/hooks/useNotificationSSE.ts`.

```ts
// Rút gọn
useEffect(() => {
  if (!token) return; // không có session thì thôi

  let es: EventSource | null = null;
  let retryDelay = INITIAL_RETRY_DELAY_MS;
  const abort = new AbortController();

  const connect = () => {
    fetch(`${baseURL}/notifications/stream`, {
      headers: {
        Authorization: `Bearer ${token}`,
        Accept: "text/event-stream",
      },
      signal: abort.signal,
    }).then((res) => {
      const reader = res.body.getReader();
      const decoder = new TextDecoder();
      // Đọc từng chunk, parse event frames theo SSE spec ("data: …\n\n")
      // Bỏ qua ": ping" heartbeat
      // Khi gặp JSON có field "id" → dispatch(addNotification(payload))
    });
  };

  connect();
  // exponential backoff từ 1s → 30s khi SSE chết
}, [token]);
```

**Điểm mạnh:**

- Token đính kèm qua `fetch` header (vì `EventSource` gốc không cho set header — đây là lý do phải dùng `fetch` + `ReadableStream`).
- Auto-reconnect với exponential backoff.
- Bỏ qua heartbeat `: ping` để tránh tốn CPU.
- Tự ngắt khi component unmount hoặc token đổi (logout).

**Dispatch duy nhất** đến store: `addNotification(payload)` → `notificationSlice` xử lý.

---

## 8. Quy tắc & best-practice đã chốt

### 8.1 Bắt buộc

| Quy tắc                                                                                                                | Lý do                                                          |
| ---------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------- |
| Chỉ dùng `useAppDispatch` / `useAppSelector` từ `@/store/hooks`                                                        | Giữ typed, tránh import thẳng `react-redux`.                   |
| Không configure axios thủ công trong component                                                                         | Mọi baseURL/timeout/header đều đã gom trong `client.ts`.       |
| Mọi slice async dùng `createAsyncThunk` với `{ rejectValue: string }`                                                  | Đảm bảo error message hiển thị được.                           |
| Persistence: chỉ `authSlice` tự lo, các slice khác tự fetch khi cần                                                    | Tránh stale data và giữ store đơn giản.                        |
| Mỗi `createAsyncThunk` fallback tiếng Việt trong `rejectWithValue`                                                     | UX nhất quán.                                                  |
| Mỗi action write của admin đi qua `mutationStatus` thông qua `addMatcher`                                              | UI chỉ cần select 1 chỗ để hiển thị toast thành công/thất bại. |
| Khi cần side-effect phức tạp trong interceptor (như dispatch), dùng `setupAuthInterceptor(...)` injection ở `main.tsx` | Tránh circular import.                                         |

### 8.2 Khuyến nghị

- Khi tạo slice mới, copy khuôn mẫu của `notificationSlice` (state + thunks + `extraReducers` + 1-2 sync reducers). Tránh thêm middleware trừ khi thật cần.
- Khi thêm `createAsyncThunk` mới, đừng quên xử lý `pending` / `rejected` (nhiều chỗ trong code chỉ có `fulfilled` — thiếu spinner/error UI).
- Khi backend đổi tên field, **không** sửa ở component; sửa ở `adapters.ts` hoặc trong service tương ứng để giữ component agnostic.
- Khi thêm hàm SSR/Node, **không** dùng `import.meta.env` trong store; chuyển `baseURL` qua tham số DI.
- `clearNotifications` / `clearStatistics` nên được dispatch song song với `logout` để tránh lộ dữ liệu giữa hai tài khoản trong cùng tab.

### 8.3 Chống chỉ định

- **Không** thêm `redux-persist` hoặc bất kỳ persistence middleware nào cho các slice khác (chỉ `authSlice` tự lo).
- **Không** dùng RTK Query; mọi call API đi qua `createAsyncThunk` để đồng nhất.
- **Không** import `apiClient` hoặc `axios` ở component; chỉ dùng `services/*`.
- **Không** gọi `forceLogout()` từ component — hàm này chỉ dành cho interceptor.

---

## 9. Bảng tra cứu nhanh

### 9.1 Đường dẫn file

| Mục đích                   | Đường dẫn                         |
| -------------------------- | --------------------------------- |
| Store root                 | `src/store/index.ts`              |
| Typed hooks                | `src/store/hooks.ts`              |
| `authSlice`                | `src/store/authSlice.ts`          |
| `userProfileSlice`         | `src/store/userProfileSlice.ts`   |
| `notificationSlice`        | `src/store/notificationSlice.ts`  |
| `statisticsSlice`          | `src/store/statisticsSlice.ts`    |
| `adminSlice`               | `src/store/adminSlice.ts`         |
| Axios client + interceptor | `src/api/client.ts`               |
| Error helper               | `src/api/errors.ts`               |
| API types                  | `src/api/types.ts`                |
| JWT helpers                | `src/api/tokenUtils.ts`           |
| Adapter (đang sơ khai)     | `src/api/adapters.ts`             |
| Service barrel             | `src/api/services/index.ts`       |
| Provider wiring            | `src/main.tsx`                    |
| SSE hook                   | `src/hooks/useNotificationSSE.ts` |

### 9.2 Tất cả 28 async thunk

| #   | Slice         | Thunk                     | Action type                           |
| --- | ------------- | ------------------------- | ------------------------------------- |
| 1   | auth          | `login`                   | `auth/login`                          |
| 2   | auth          | `register`                | `auth/register`                       |
| 3   | userProfile   | `fetchCurrentUserProfile` | `userProfile/fetchCurrentUserProfile` |
| 4   | notifications | `fetchNotifications`      | `notifications/fetchAll`              |
| 5   | notifications | `fetchUnreadCount`        | `notifications/fetchUnreadCount`      |
| 6   | notifications | `markAsRead`              | `notifications/markAsRead`            |
| 7   | notifications | `markAllAsRead`           | `notifications/markAllAsRead`         |
| 8   | statistics    | `fetchStatisticsOverview` | `statistics/fetchOverview`            |
| 9   | statistics    | `fetchRevenue`            | `statistics/fetchRevenue`             |
| 10  | statistics    | `fetchTopProducts`        | `statistics/fetchTopProducts`         |
| 11  | statistics    | `fetchStudents`           | `statistics/fetchStudents`            |
| 12  | statistics    | `fetchQuizAttempts`       | `statistics/fetchQuizAttempts`        |
| 13  | admin         | `fetchAdminDashboard`     | `admin/fetchDashboard`                |
| 14  | admin         | `fetchAdminUsers`         | `admin/fetchUsers`                    |
| 15  | admin         | `lockAdminUser`           | `admin/lockUser`                      |
| 16  | admin         | `unlockAdminUser`         | `admin/unlockUser`                    |
| 17  | admin         | `changeAdminUserRole`     | `admin/changeUserRole`                |
| 18  | admin         | `resetAdminUserPassword`  | `admin/resetUserPassword`             |
| 19  | admin         | `fetchPendingProducts`    | `admin/fetchPendingProducts`          |
| 20  | admin         | `approveAdminProduct`     | `admin/approveProduct`                |
| 21  | admin         | `rejectAdminProduct`      | `admin/rejectProduct`                 |
| 22  | admin         | `fetchAdminConfigs`       | `admin/fetchConfigs`                  |
| 23  | admin         | `updateAdminConfig`       | `admin/updateConfig`                  |

(Một số slice có thêm helper thunks / fetch phụ trợ không nằm trong bảng — xem thẳng trong file slice tương ứng.)

### 9.3 Tất cả sync action

| Slice         | Actions                                                                                                           |
| ------------- | ----------------------------------------------------------------------------------------------------------------- |
| auth          | `logout`, `clearAuthError`, `setCredentials`                                                                      |
| userProfile   | `clearUserProfile`                                                                                                |
| notifications | `addNotification`, `clearNotifications`                                                                           |
| statistics    | `clearStatistics`                                                                                                 |
| admin         | `setUsersRoleFilter`, `setUsersPage`, `setUsersSize`, `setProductsPage`, `setProductsSize`, `clearMutationStatus` |

### 9.4 Vòng đời của 1 request

```text
Component
   dispatch(asyncThunk(...))       [useAppDispatch]
        │
        ▼
Slice extraReducers.pending       → state.status = "loading"
        │
        ▼
Service.method(...)  (axios call) → apiClient.request
        │
        ▼
[401?]
   ├── không      → response bình thường → extraReducers.fulfilled → state.status = "succeeded"
   └── có         → interceptor chạy refresh-token:
                       ├── thành công   → setAuthToken + dispatch(setCredentials) + retry
                       └── thất bại     → forceLogout() → navigate /auth/login
        │
        ▼
Component re-render với state mới
```

---

## Phụ lục: Mở rộng dự án

### Thêm 1 slice mới

```text
1. Tạo src/store/<domain>Slice.ts theo khuôn mẫu notificationSlice.
2. Bổ sung reducer vào store: import … reducer from "./<domain>Slice";
3. Thêm API service tương ứng ở src/api/services/<domain>.ts + export vào index.ts.
4. Nếu cần interceptor mới (không chỉ 401), đăng ký trong client.ts; nếu cần dispatch từ interceptor,
   dùng setupAuthInterceptor(...) hoặc tạo một setupXXXInterceptor(...) song song.
5. Mount đầu tiên: ưu tiên fetch dữ liệu trong layout (StudentDashboardLayout / TeacherDashboardLayout / admin route).
6. Khi logout: dispatch clearXxx để tránh stale.
```

### Thêm 1 domain service

```text
1. Tạo src/api/services/<domain>.ts.
2. Mỗi method là async, await apiClient.<verb>, trả response.data (đã unwrap) hoặc ApiResponse<T> nếu muốn giữ envelope (chọn 1 style cho cả file).
3. Cập nhật src/api/services/index.ts (barrel).
4. Thêm DTO vào src/api/types.ts (không dùng any cho payload).
5. Test thủ công: mở DevTools Network, đảm bảo Authorization header được gắn nếu cần.
```

### Đổi backend response shape

```text
1. Cập nhật src/api/types.ts (DTO mới).
2. Nếu là snake_case → camelCase, viết helper trong adapters.ts rồi gọi trong service tương ứng.
3. Sửa slice extraReducers.fulfilled để map field mới.
4. KHÔNG sửa ở component.
```

---

> Tài liệu này được viết cho nhánh `master`, commit gần nhất tại thời điểm tạo (xem `git log` để biết chính xác). Khi cấu trúc slice/endpoint thay đổi, hãy cập nhật tương ứng.
