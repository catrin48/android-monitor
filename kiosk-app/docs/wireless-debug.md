# タブレットのワイヤレスデバッグ手順

館内モニター用タブレットに、PC から Wi-Fi 経由で `adb` 接続するための手順。
USB 接続が不安定 / ケーブルが給電専用でも、これで PC から操作できる。

## 対象端末

| 項目 | 値 |
|---|---|
| モデル | Defender D23（ビルド名 GM92 / MIRAMAGE 系） |
| Android | 13（API 33） |
| SoC | MediaTek MT8788 / RAM 8GB |
| 接続時の Wi-Fi | SSID `tsubaki-kanshi`（PC と同一ネットワークに置くこと） |

Android 11 以降なので「ペア設定コード方式」を使う（Android 10 以前の `adb tcpip` とは手順が違う）。

## 前提

- PC に `adb` が入っていること
  ```bash
  sudo apt install -y android-tools-adb   # 例: Ubuntu
  adb version
  ```
- タブレットと PC が**同じ Wi-Fi / 同じサブネット**にいること
  （例: PC `192.168.2.191`、タブレット `192.168.2.x`）

## 手順

### 1. タブレット側：開発者オプションを有効化

`設定 → デバイス情報 → ビルド番号` を 7 回タップ。

### 2. タブレット側：ワイヤレスデバッグを ON

`設定 → システム → 開発者向けオプション → ワイヤレスデバッグ` を ON。

### 3. タブレット側：ペア設定画面を開く

「ワイヤレスデバッグ」の**文字自体をタップ**して詳細画面へ。
→ **「ペア設定コードによるデバイスのペア設定」** をタップ。

画面に次が表示される（値は毎回変わる）:

```
IP アドレスとポート  192.168.2.XX:YYYYY   ← ペアリング用ポート
Wi-Fi ペア設定コード  NNNNNN               ← 6 桁
```

### 4. PC 側：ペアリング（初回のみ）

```bash
adb pair 192.168.2.XX:YYYYY NNNNNN
# 例: adb pair 192.168.2.92:46079 011415
# → Successfully paired to ...
```

> コードには時間制限がある。表示されたらすぐ実行する。

### 5. PC 側：接続

ワイヤレスデバッグの**トップ画面**（ペア設定ダイアログを閉じた後）に出ている
`IP アドレスとポート` を使う。**ペアリング用とは別のポート**。

```bash
adb connect 192.168.2.XX:ZZZZZ
# 例: adb connect 192.168.2.92:38639
adb devices -l
# 192.168.2.92:ZZZZZ   device   product:Defender model:Defender_D23
```

`device` と出れば成功。以降 `adb -s 192.168.2.92:ZZZZZ <コマンド>` で操作できる。

## 実績値（2026-09-04 時点・参考）

| 項目 | 値 |
|---|---|
| タブレット IP | `192.168.2.92` |
| ペアリング用ポート | `46079`（例。毎回変わる） |
| 接続用ポート | `38639`（例。毎回変わる） |
| ペアリング GUID | `adb-TA4FOXBJDXL5ZRPS066-...` |

> ポート番号は再起動・Wi-Fi 再接続で変わる。IP も DHCP なら変わり得る
> （ルーターで MAC 予約して固定推奨）。

## 再接続（2 回目以降）

- **ペアリングは記憶される**ので、通常は `adb connect` だけでよい。
- ただし**接続用ポートが変わる**ため、その都度タブレットの
  「ワイヤレスデバッグ」トップ画面で現在のポートを確認する。
- ポートが不明で総当たりしたくない場合:
  ```bash
  # 同一 LAN から mDNS で探す（環境依存）
  adb mdns services
  ```
- 再起動でペアリング自体が消えた場合は手順 3〜5 をやり直す。

## よく使うコマンド

```bash
D=192.168.2.92:38639   # ← 実際の接続ポートに置き換え

# アプリのインストール / 更新
adb -s $D install -r kiosk-app/app/build/outputs/apk/debug/app-debug.apk

# キオスクアプリの起動 / 強制終了
adb -s $D shell am start -n com.hanatsubaki.kiosk/.MainActivity
adb -s $D shell am force-stop com.hanatsubaki.kiosk

# スクリーンショット（※ WebView 部分は白く写ることがある）
adb -s $D exec-out screencap -p > shot.png

# ログ
adb -s $D logcat -d | grep -iE "hanatsubaki|chromium|camera"

# カメラ権限の付与（Device Owner 前は手動）
adb -s $D shell pm grant com.hanatsubaki.kiosk android.permission.CAMERA

# 保存された記念撮影の確認
adb -s $D shell ls -la "/sdcard/Pictures/花つばき記念撮影/"

# Device Owner 設定（※ 事前に端末から Google アカウントを全削除）
adb -s $D shell dpm set-device-owner com.hanatsubaki.kiosk/.KioskAdminReceiver
```

## 補足：EShare の無効化

この端末にはワイヤレス投影アプリ EShare がプリインストールされており、
画面左右端に浮遊ハンドル（矢印マーク）を常時オーバーレイ表示する。
キオスクでは不要なので無効化した（元に戻すには `pm install-existing`）:

```bash
adb -s $D shell pm disable-user --user 0 com.eshare.paint
adb -s $D shell pm disable-user --user 0 com.ecloud.eshare.server
```

## 外部（旅館外）からの接続について

`adb` は LAN 内前提。旅館外から保守する場合は別途:

- 旅館 LAN に常時起動の小型 PC / Raspberry Pi を置き、Tailscale などで接続
  → その PC から LAN 内のタブレットに `adb`
- または APK に自己更新機能を実装し、`adb` を使う場面自体を減らす

詳細は別途検討。
