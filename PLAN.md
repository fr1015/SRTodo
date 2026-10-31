# 開発計画・引き継ぎメモ

最終更新: 2026-10-31
進捗: Phase 0 完了（雛形を作成し、assembleDebug が通る状態。初回コミット済み）
デザイン: 作成中のため未定。決まるまでは Material3 標準の仮デザインで進める

## 決定済みの仕様
| 項目 | 決定内容 |
|---|---|
| 期限なしタスク | 許可する。並び順では最後（期限ありを期限の近い順に並べ、その後ろ） |
| タスク0件のとき | 通知を消す（`cancel`）。0件のときはスワイプされても再投稿しない／再起動後も表示しない。タスクが追加されたら再表示する |
| 期限切れタスク | 通知内で赤字で表示する（アプリ画面も同じ扱いにする） |
| 期限の粒度 | 日付と時刻（日時ピッカーで設定。`dueAt: Long?` に epoch millis で保存） |

## 方針
- 一番難しいのは通知まわり（RemoteViews のチェックボックスと常駐性）。そのため通知を先に試作して動作を確かめ、アプリ画面は後で作る
- デザインに関係ない部分（データ層・通知の仕組み）を先に作り、見た目は後で差し替えられる構成にする
- DIは手書き（Hiltは使わない）
- DBへの書き込みは Repository 経由に限定し、書き込みのたびに `NotificationUpdater` で通知を作り直す

## 段取り
- [x] **Phase 0：プロジェクトの準備**：`git init`、Compose の雛形を作る（minSdk 31）。Room / KSP / Coroutines を追加し、`./gradlew assembleDebug` が通ることを確認
- [ ] **Phase 1：通知の試作（最優先）**：RemoteViews の CheckBox と `setOnCheckedChangeResponse`（API 31以降）で、タップが BroadcastReceiver に届くか確かめる。カスタムレイアウトが通知の高さ制限に収まるかも確認。タスクは固定データでよい
- [ ] **Phase 2：データ層**：`Task`（id, title, dueAt: Long?, isDone, createdAt, completedAt）。DAO は未完了のタスクを期限の近い順に上位3件取る（`ORDER BY dueAt IS NULL, dueAt ASC`）。Repository と NotificationUpdater を作る
- [ ] **Phase 3：通知を消えにくくする**：チェックで完了したら Receiver 内で `goAsync()` を使ってDBを更新し、通知を出し直す（次のタスクが繰り上がる）。`setDeleteIntent` でスワイプされたら再投稿する（Android 14以降は ongoing 通知も消せるため必須）。`BOOT_COMPLETED` と `MY_PACKAGE_REPLACED` で再表示。`POST_NOTIFICATIONS` の権限リクエスト（Android 13以降）と通知チャンネルを用意する
- [ ] **Phase 4：アプリ画面（仮デザイン）**：一覧、追加・編集（日時ピッカー）、削除、完了済みの表示。色や文字サイズは Theme にまとめておく
- [ ] **Phase 5：デザインを反映**：Compose は Theme と各コンポーネントを差し替え、通知は XML レイアウトを差し替える
- [ ] **Phase 6：仕上げ**：DAOの並び順と繰り上がりのテスト。実機で再起動、スワイプで消す、ダークモード、0件→1件の再表示、期限切れの赤字表示を確認

## 通知デザインの制約（デザイナー向け）
- 通知の中身は RemoteViews のため Compose は使えない。使える View が限られる（TextView, CheckBox, ImageView, LinearLayout など）
- 高さに上限がある（展開時でも約256dp）。折りたたみ時に3件すべては入らない可能性があるので、「折りたたみ時は1件、展開すると3件」を想定する
- 通知の外枠（アプリ名・アイコンの行）はシステムが描く。デザインできるのは中身の部分だけ
- ライトとダーク、両方のテーマに対応すること

## ビルド環境（Phase 0 で確定）
- JDK 21（Temurin）。インストールされているのは21だけなので、ツールチェーンは指定せず、`jvmTarget` と `compileOptions` で 17 を指定している
- Gradle 8.14.5 / AGP 8.13.2 / Kotlin 2.4.20 / KSP 2.3.13 / Room 2.8.5（Room Gradle Plugin でスキーマを `app/schemas` に出力）
- compileSdk / targetSdk 36（SDKに入っているのは android-36 まで）
- **Compose BOM は 2026.06.00（Compose 1.11 系）に固定**。2026.08.00 以降（Compose 1.12）は AGP 9.1 以上と compileSdk 37 が必要なため使えない。上げるときは AGP 9 系への移行と SDK 37 の導入をあわせて行うこと
- パッケージ名 / applicationId: `com.sptodo.app`
- バージョンは `gradle/libs.versions.toml` で管理している
- ビルド時の「Unable to strip libandroidx.graphics.path.so」は NDK が未導入なだけで、無視してよい

## 次にやること
Phase 1（通知の試作）に着手する
