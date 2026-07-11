# Travel Adventure - MVP

這是一個以 Spring Boot 製作的台灣旅行冒險 MVP，包含城市探索、景點打卡、玩家進度、經驗值與金幣獎勵。

## 技術

- Spring Boot
- Spring Data JPA
- Spring Security + JWT
- MySQL
- MySQL Connector/J
- 前端靜態頁面位於 `src/main/resources/static`

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

## 建立 MySQL 資料庫

第一次使用前可先建立資料庫：

```sql
CREATE DATABASE travel_adventure CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

## 測試

```powershell
.\mvnw.cmd test
```
