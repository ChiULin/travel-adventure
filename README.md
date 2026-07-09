# Travel Adventure - MVP

這是一個使用 Spring Boot 開發的旅遊冒險 MVP。玩家可以登入、探索城市景點、完成打卡、抽卡、升級技能並挑戰城市 Boss。

## 技術棧

- Spring Boot
- Spring Data JPA
- Spring Security + JWT
- MySQL
- MySQL Connector/J
- 靜態前端放在 `src/main/resources/static`

## 本機執行

請先啟動 MySQL，並設定資料庫密碼：

```powershell
$env:DB_USERNAME="root"
$env:DB_PASSWORD="你的密碼"
$env:JWT_SECRET="至少 32 位元組的隨機密鑰"
.\mvnw.cmd spring-boot:run
```

啟動後開啟：

```text
http://localhost:8080
```

## 建立 MySQL 資料庫

首次執行前建立資料庫：

```sql
CREATE DATABASE travel_adventure CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

VS Code 的 `Run DemoApplication` 會讀取 Windows 使用者環境變數。

## 常用指令

```bash
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```
