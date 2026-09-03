# 館内マップの場所写真

タッチモニターは**静的サイト**なので、写真は**このフォルダに置いて git に push する**だけ。
アップロード画面や API はない。

## 置き方

```
facility-map/photos/<場所ID>/1.jpg
facility-map/photos/<場所ID>/2.jpg   ← 複数枚は連番
facility-map/photos/<場所ID>/3.jpg
```

例：フロントの写真 → `facility-map/photos/front/1.jpg` `2.jpg` `3.jpg`

- 形式: JPG（推奨。横1600px程度・1枚300KB前後にリサイズしておくと表示が速い）
- ファイル名: `1.jpg` `2.jpg` … の連番（`facility-map.html` の SPOTS で参照している）

## 反映手順

```bash
cd android-monitor
git add facility-map/photos
git commit -m "館内マップ: フロントの写真を追加"
git push
```
→ Render Static Site に自動反映（自動デプロイが効かない場合は Render で Manual Deploy）

## ピン（📷ボタン）の追加・位置調整

`facility-map/facility-map.html` の `SPOTS` を編集：

```js
const SPOTS = {
  2: [
    { x: 71, y: 76, label: 'フロント', type: 'gallery',
      photos: ['photos/front/1.jpg', 'photos/front/2.jpg', 'photos/front/3.jpg'] }
  ],
  ...
};
```

- `x`, `y` … そのフロア図（`maps/floorN.jpg`）に対する **％位置**（左上が 0,0 / 右下が 100,100）
- `label` … ピンに出る名前
- `photos` … 表示する写真パスの配列（この photos/ フォルダからの相対）

新しい場所を足すときは、対応するフォルダを作って写真を入れ、SPOTS に1行足す。

## 現在の登録

| フロア | 場所 | フォルダ | 状態 |
|---|---|---|---|
| 2階 | フロント | `photos/front/` | 写真未配置（配置すると 📷 で表示される） |
