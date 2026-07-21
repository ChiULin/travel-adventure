# Travel Adventure - MVP

這是一個以 Spring Boot 製作的台灣旅行冒險 MVP，包含城市探索、景點打卡、玩家進度、經驗值與金幣獎勵。

## 技術

- Spring Boot
- Spring Data JPA
- Spring Security + JWT
- MySQL
- MySQL Connector/J
- 前端靜態頁面位於 `src/main/resources/static`

## 前端結構

前端維持原生 HTML、CSS 與 JavaScript，依功能拆分：

```text
static/
├─ index.html
├─ css/
│  ├─ base.css
│  ├─ game.css
│  └─ components.css
├─ js/
│  ├─ state.js
│  ├─ api.js
│  ├─ auth.js
│  ├─ journey.js
│  ├─ quiz.js
│  ├─ boss.js
│  ├─ missions.js
│  ├─ collection.js
│  └─ app.js
└─ images/
```

`index.html` 以 `defer` 依序載入腳本，最後由 `app.js` 綁定事件並啟動頁面。

## API 回應格式

所有 `/api/**` 成功與錯誤回應都使用相同 envelope：

```json
{
  "success": true,
  "message": "操作成功",
  "data": {}
}
```

錯誤回應的 `success` 為 `false`，且 `data` 為 `null`。前端只在 `js/api.js` 驗證 envelope 並取出 `data`。

## 題目簽發狀態

題目暫存於單機記憶體，回應包含 `issuedAt` 與 `expiresAt`。提交後題目會立即失效，系統也會定期清除過期題目與防重複題目的暫存紀錄。

```properties
game.quiz.max-pending-per-player=5
game.quiz.cleanup-interval-ms=60000
```

同一玩家預設最多保留 5 題未完成題目；同一關卡重新取題會取代前一題，不會額外占用配額。多節點部署時應將這段狀態改存 Redis 或共享資料庫。

## 本機啟動

請先建立 MySQL 資料庫，並用環境變數提供敏感資訊；不要把資料庫密碼或 JWT secret 寫進 Git 追蹤的設定檔。

```powershell
$env:MYSQL_PASSWORD="你的 MySQL 密碼"
$env:JWT_SECRET="至少 32 位元組的隨機密鑰"
.\mvnw.cmd spring-boot:run
```

啟動後開啟：

```text
http://localhost:8080
```

### H2 本機環境

不連接 MySQL 時，可以使用 `local` profile 啟動 H2 記憶體資料庫：

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

`local` 與 `test` profile 會由 `src/main/resources/data.sql` 載入城市與景點資料。請勿另外加入會新增或修改同一批資料的 `CommandLineRunner`，以免產生重複或不一致的景點。

## 建立 MySQL 資料庫

第一次使用前可先建立資料庫：

```sql
CREATE DATABASE travel_adventure CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

預設 MySQL 環境設定為 `spring.sql.init.mode=never`，不會自動執行 `data.sql`；正式資料應由資料庫遷移或部署流程管理。

## 測試

```powershell
.\mvnw.cmd test
```
