# Mahjong 台灣 16 張麻將 3D 版

適用於 **Paper 26.2** 的繁體中文 Minecraft 台灣麻將插件。

- 插件版本：`3.0.0`
- 遊戲規則：台灣 16 張麻將
- 顯示方式：3D 麻將牌、實體牌桌與互動按鈕
- 支援模式：4 位真人，或以電腦玩家補滿
- 執行環境：Java 21

> [!WARNING]
> ## 安裝前請先刪除舊版本
>
> 請將以下舊版插件或其他同名麻將插件移出伺服器的 `plugins` 資料夾：
>
> - `MahjongPlay-1.2.0.jar`
> - `MahjongPlay-TW-26.2-2.0.0.jar`
> - 其他同名 MahjongPlay 插件
>
> 同時安裝多個版本會造成 `/mahjong` 指令衝突，並可能載入錯誤版本。

---

## 目錄

- [插件安裝](#插件安裝)
- [建立第一張牌桌](#建立第一張牌桌)
- [材質包安裝](#材質包安裝)
- [快速使用](#快速使用)
- [遊戲操作](#遊戲操作)
- [採用規則](#採用規則)
- [權限](#權限)
- [檔案校驗值](#檔案校驗值)
- [故障排除](#故障排除)
- [測試狀態](#測試狀態)

---

## 插件安裝

1. 關閉 Minecraft 伺服器。
2. 將 `MahjongPlay-TW-26.2-3D-3.0.0.jar` 放入伺服器的 `plugins` 資料夾。
3. 啟動伺服器。
4. 輸入 `/plugins`，確認 `MahjongPlayTW` 顯示為綠色。

> [!IMPORTANT]
> 更新或移除舊版本後，請完整重新啟動伺服器，不要只使用 `/reload`。

---

## 建立第一張牌桌

1. 站在平坦、空曠的位置。
2. 面向要放置牌桌的方向。
3. 輸入：

```text
/mahjong create 我的牌桌
```

牌桌會建立在玩家前方約 4 格處，桌面與座位共占用約 **7 × 7 格**。

> [!CAUTION]
> 請勿直接在重要建築內建立牌桌。
>
> 刪除牌桌時請使用：
>
> ```text
> /mahjong destroy <編號>
> ```
>
> 不要手動破壞桌子，否則可能留下顯示實體或互動實體。

---

## 材質包安裝

未安裝材質包時，麻將牌會顯示為普通紙張。

### 方式 A：每位玩家自行安裝

1. 將 `MahjongPlay-TW-26.2-3D-ResourcePack.zip` 放入 Minecraft 材質包資料夾。

Windows 路徑：

```text
%APPDATA%\.minecraft\resourcepacks
```

2. 開啟 Minecraft。
3. 進入「選項」→「資源包」。
4. 啟用「MahjongPlay 台灣麻將 3D」。

> [!NOTE]
> 不要解壓縮材質包 ZIP。

### 方式 B：伺服器自動發送

1. 將 `MahjongPlay-TW-26.2-3D-ResourcePack.zip` 上傳至可直接下載檔案的 HTTPS 網址。
2. 編輯 `server.properties`：

```properties
resource-pack=<材質包 ZIP 的直接下載網址>
resource-pack-sha1=f03dd1ca5d63db9103f38192ca16ebfa0aba248a
require-resource-pack=true
```

3. 儲存檔案並重新啟動伺服器。

伺服器提供的網址必須是 ZIP 檔案的直接下載網址，不可使用一般網頁分享頁面。

---

## 快速使用

| 指令 | 說明 | 權限需求 |
|---|---|---|
| `/mahjong create [名稱]` | 建立新牌桌 | 管理員 |
| `/mahjong list` | 查看現有牌桌 | 無 |
| `/mahjong join [編號]` | 加入指定牌桌，也可右鍵桌中央 | 無 |
| `/mahjong ready` | 切換準備狀態，也可再次右鍵桌中央 | 無 |
| `/mahjong bot` | 加入一位電腦玩家 | 無 |
| `/mahjong start` | 以電腦玩家補滿並開始遊戲 | 管理員 |
| `/mahjong leave` | 離開目前牌桌 | 無 |
| `/mahjong score` | 查看目前分數 | 無 |
| `/mahjong rules` | 查看插件採用的台麻規則 | 無 |
| `/mahjong rerender` | 重新生成牌與操作按鈕的顯示實體 | 無 |
| `/mahjong protection <編號> <on\|off>` | 切換指定牌桌的方塊保護；新牌桌預設開啟 | 管理員 |
| `/mahjong destroy <編號>` | 刪除牌桌及其顯示實體 | 管理員 |

---

## 遊戲操作

- 自己的手牌會顯示正面。
- 其他玩家看到的是牌背。
- 輪到自己時，右鍵自己的 3D 麻將牌即可出牌。
- 可進行吃、碰、槓、胡時，牌桌附近會出現可右鍵的浮空操作按鈕。
- 支援 4 位真人玩家。
- 人數不足時可加入電腦玩家補滿。

---

## 採用規則

- 台灣 16 張麻將。
- 使用完整 144 張牌，包含八張花牌。
- 莊家起手 17 張，其餘玩家起手 16 張。
- 支援吃、碰、明槓、暗槓、自摸、放槍與一炮多響。
- 胡牌基本結構為 5 組面子加 1 對將眼。
- 台數與細則採插件內建固定版本。

遊戲中可輸入以下指令查看規則：

```text
/mahjong rules
```

---

## 權限

建立牌桌需要：

```text
mahjongplay.command.create
```

切換牌桌方塊保護需要：

```text
mahjongplay.command.protection
```

若未設定權限插件，也可直接授予玩家 OP 權限。

管理員操作包括：

- 建立牌桌
- 強制開始遊戲
- 刪除牌桌

---

## 檔案校驗值

### 材質包 SHA-1

```text
f03dd1ca5d63db9103f38192ca16ebfa0aba248a
```

### 插件 JAR SHA-256

```text
bda49e5c6dedf47461b3ddbfb488a967b604c32dc3b80236c3653816991887ce
```

可使用校驗值確認下載的檔案未損壞、未被重新壓縮或修改。

---

## 故障排除

### 牌桌完全沒有生成

請依序確認：

1. 玩家具有 OP 權限，或具有：

   ```text
   mahjongplay.command.create
   ```

2. 舊版 JAR 已從 `plugins` 資料夾移除。
3. 伺服器已完整重新啟動，而不是只執行 `/reload`。
4. 輸入 `/plugins`，確認 `MahjongPlayTW` 顯示為綠色。
5. 查看伺服器 Console 是否出現與 `MahjongPlayTW` 有關的錯誤。

### 桌子有生成，但牌顯示為紙張

可能原因：

- 玩家尚未啟用材質包。
- 伺服器提供的材質包網址不是 ZIP 直接下載網址。
- 材質包被下載網站重新壓縮或修改。
- `resource-pack-sha1` 與實際檔案不一致。

請重新核對材質包 SHA-1：

```text
f03dd1ca5d63db9103f38192ca16ebfa0aba248a
```

### 桌子有生成，但 3D 牌或按鈕消失

輸入：

```text
/mahjong rerender
```

伺服器重新啟動後，進行中的牌局會回到大廳狀態。玩家需要重新加入並開始遊戲。

---

## 測試狀態

已通過：

- 台麻胡牌與流程引擎自動測試
- 插件主類別載入測試
- 建立牌桌與生成方塊模擬測試
- 3D `ItemDisplay` 生成模擬測試
- `Interaction` 互動實體模擬測試

目前狀態：

- JAR 使用 Java 21 bytecode 編譯。
- 可在較新的 Java 執行環境載入。
- 尚未在真實 Paper 26.2 多人伺服器完成完整實戰測試。

> [!TIP]
> 若啟動或遊戲過程發生錯誤，回報問題時請附上完整伺服器 Console 錯誤紀錄。

---

## 回報問題

回報問題時，建議提供：

- Paper 版本
- Java 版本
- 插件版本
- 是否安裝其他麻將或指令相關插件
- 完整 Console 錯誤紀錄
- 問題發生前執行的指令或操作步驟
