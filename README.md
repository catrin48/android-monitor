# android-monitor — 館内タッチモニター

館内ロビー等に置く Android タッチモニター用の案内画面。
1つの URL を全画面表示し、そこから各コンテンツへ移動する。

## 構成

```
index.html                     … メニュー（トップ画面）
facility-map/facility-map.html  … 館内マップ（3Dフロア図）
  ├ maps/floor1〜5.jpg
  ├ vendor/pannellum.*          … 360°ビューア
  └ room_picture/              … 客室写真（静的ホストでは表示不可・後述）
render.yaml                     … Render Static Site 設定
```

メニューのリンク:
| タイル | リンク先 |
|---|---|
| 館内マップ | `facility-map/facility-map.html`（同梱） |
| 温泉・サウナの温度 | `https://bath-board-1az8.onrender.com/allcut`（外部・温度システム） |
| ゲーム | 準備中（`games/` を追加予定。企画は `bath_system/shared/docs/lobby-kiosk.md`） |

## デプロイ（Render Static Site / catrin48）

1. GitHub: `catrin48/android-monitor` を作成（public/private どちらでも可）
2. push
   ```bash
   cd android-monitor
   git init && git add -A && git commit -m "初期: メニュー + 館内マップ"
   git branch -M main
   git remote add origin git@github.com:catrin48/android-monitor.git
   git push -u origin main
   ```
3. Render → **New → Static Site** → `catrin48/android-monitor`
   - Build Command: （空欄）
   - Publish Directory: `.`
   - → `https://android-monitor-xxxx.onrender.com`

`render.yaml` を置いてあるので、Render 側で Blueprint から読み込ませても可。

## モニター端末（Android）のキオスク設定

- 「Fully Kiosk Browser」等で上記 URL を固定
- スリープ無効、ナビバー非表示、画面向きロック（横）
- 放置対策：各サブページに「メニューに戻る」＋無操作でトップへ戻す common.js を今後追加

## 既知の制限

- 館内マップの客室写真（📷 の部屋をタップ）は `/api/room-photos/` を叩くため、
  静的ホストでは表示できない（「写真がありません」表示になる）。フロアマップ表示は正常。
  → 写真も出したい場合は QR プロジェクトの `server.js` 側、または API を別途用意。

## 元データ

館内マップは `bath_system/deploy_cloud/static/facility-map/` からのコピー。
本家を更新したら、このフォルダにも反映すること（2コピー）。
