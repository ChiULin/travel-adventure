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
│  ├─ exploration.js
│  ├─ image-recognition.js
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

題目、探索、圖片辨識與 Boss 戰鬥暫存於單機記憶體，回應包含 `issuedAt` 與 `expiresAt`。提交後狀態會立即失效，系統也會定期清除過期紀錄。

```properties
game.quiz.max-pending-per-player=5
game.quiz.cleanup-interval-ms=60000
game.exploration.cleanup-interval-ms=60000
game.image-recognition.cleanup-interval-ms=60000
game.puzzle.cleanup-interval-ms=60000
game.boss.cleanup-interval-ms=60000
```

同一玩家預設最多保留 5 題未完成題目；同一關卡重新取題會取代前一題，不會額外占用配額。目前部署模型明確限制為單一應用節點；多節點部署前必須將全部挑戰狀態改存 Redis 或共享資料庫。

圖片辨識與拼圖的圖片、候選景點及正解統一設定於 `VisualChallengeRegistry`，使用 `cityOrder + stageOrder` 作為穩定識別，啟動時才解析實際景點 ID。啟動驗證會阻擋缺少素材、候選重複或正解未列入候選的設定。

臺北、臺中、臺南、高雄與花蓮的十五個景點皆已啟用未知挑戰。已加入未知挑戰池的景點不能再從舊的探索、問答或圖片出題入口直接開始，必須先由 Mystery API 建立並重用玩家專屬 Session。

目前拼圖採展示型 MVP：拼圖片排列完成由前端判斷；最終景點答案、Session 期限、玩家歸屬與獎勵則由後端驗證。若未來需要防止直接呼叫完成 API，應新增後端交換 API 並保存目前排列。

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

預設 MySQL 環境設定為 `spring.sql.init.mode=never`，不會自動執行 `data.sql`。既有資料庫第一次啟動 Flyway 時會建立 baseline，之後依 `src/main/resources/db/migration` 的版本腳本更新；JPA 僅驗證 schema，不再自行修改。

## 測試

```powershell
.\mvnw.cmd test
```
