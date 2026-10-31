# 開発計画・引き継ぎメモ

最終更新: 2026-10-31
進捗: Phase 4 完了（アプリ画面の仮デザイン、期限切れ表示の自動更新。エミュレータ（API 36）で確認済み）
デザイン: 作成中のため未定。決まるまでは Material3 標準の仮デザインで進める

## 決定済みの仕様
| 項目 | 決定内容 |
|---|---|
| 期限なしタスク | 許可する。並び順では最後（期限ありを期限の近い順に並べ、その後ろ） |
| タスク0件のとき | 通知を消す（`cancel`）。0件のときはスワイプされても再投稿しない／再起動後も表示しない。タスクが追加されたら再表示する |
| 期限切れタスク | 通知内で赤字で表示する（アプリ画面も同じ扱いにする） |
| 期限の粒度 | 日付と時刻（日時ピッカーで設定。`dueAt: Long?` に epoch millis で保存） |
| 期限切れへの切り替わり | 期限を過ぎたら、操作がなくても通知を赤字に作り直す（`AlarmManager` の RTC アラーム。数分の遅れは許容）。アプリ画面は毎分0秒に表示を更新する |

## 方針
- 一番難しいのは通知まわり（RemoteViews のチェックボックスと常駐性）。そのため通知を先に試作して動作を確かめ、アプリ画面は後で作る
- デザインに関係ない部分（データ層・通知の仕組み）を先に作り、見た目は後で差し替えられる構成にする
- DIは手書き（Hiltは使わない）
- DBへの書き込みは Repository 経由に限定し、書き込みのたびに `NotificationUpdater` で通知を作り直す

## 段取り
- [x] **Phase 0：プロジェクトの準備**：`git init`、Compose の雛形を作る（minSdk 31）。Room / KSP / Coroutines を追加し、`./gradlew assembleDebug` が通ることを確認
- [x] **Phase 1：通知の試作（最優先）**：RemoteViews の CheckBox と `setOnCheckedChangeResponse`（API 31以降）で、タップが BroadcastReceiver に届くか確かめる。カスタムレイアウトが通知の高さ制限に収まるかも確認。タスクは固定データでよい
- [x] **Phase 2：データ層**：`Task`（id, title, dueAt: Long?, isDone, createdAt, completedAt）。DAO は未完了のタスクを期限の近い順に上位3件取る（`ORDER BY dueAt IS NULL, dueAt ASC`）。Repository と NotificationUpdater を作る
- [x] **Phase 3：通知を消えにくくする**：~~チェックで完了したら Receiver 内で `goAsync()` を使ってDBを更新し、通知を出し直す~~（Phase 2 で前倒し済み）。`setDeleteIntent` でスワイプされたら再投稿する（Android 14以降は ongoing 通知も消せるため必須）。`BOOT_COMPLETED` と `MY_PACKAGE_REPLACED` で再表示。`POST_NOTIFICATIONS` の権限リクエスト（Android 13以降）と通知チャンネルを用意する（Phase 1 で対応済み）
- [x] **Phase 4：アプリ画面（仮デザイン）**：一覧、追加・編集（日時ピッカー）、削除、完了済みの表示。色や文字サイズは Theme にまとめておく
- [ ] **Phase 5：デザインを反映**：Compose は Theme と各コンポーネントを差し替え、通知は XML レイアウトを差し替える
- [ ] **Phase 6：仕上げ**：DAOの並び順と繰り上がりのテスト。実機で再起動、スワイプで消す、ダークモード、0件→1件の再表示、期限切れの赤字表示を確認

## 通知デザインの制約（デザイナー向け）
- 通知の中身は RemoteViews のため Compose は使えない。使える View が限られる（TextView, CheckBox, ImageView, LinearLayout など）
- 高さに上限がある（展開時でも約256dp）。折りたたみ時に3件すべては入らない可能性があるので、「折りたたみ時は1件、展開すると3件」を想定する
- 通知の外枠（アプリ名・アイコンの行）はシステムが描く。デザインできるのは中身の部分だけ
- ライトとダーク、両方のテーマに対応すること

## データ層の実装メモ（Phase 2）
- 構成（`com.srtodo.app`）
  - `data/Task`（エンティティ、テーブル名 `tasks`、`(isDone, dueAt)` にインデックス）、`data/TaskDao`、`data/AppDatabase`（DB名 `srtodo.db`、version 1、スキーマは `app/schemas` に出力してコミットする）
  - `data/TaskRepository`：書き込みの窓口。`add` / `edit` / `complete` / `uncomplete` / `delete` のたびに `NotificationUpdater.update()` を呼ぶ
  - `notification/NotificationUpdater`：DB を読んで通知を作り直す（読むだけ）。`Mutex` で更新を1つずつ順番に行う
  - `AppContainer`（手書き DI）を `SRTodoApplication` が持つ。`context.appContainer` で取り出す。通知チャンネルは `Application.onCreate` で作る
- 並び順：`ORDER BY dueAt IS NULL, dueAt ASC, createdAt ASC, id ASC`（同じ期限・期限なし同士は作成が早い順）。完了済みは `completedAt DESC`
- Receiver は `goAsync()` ＋ `Dispatchers.IO` のコルーチンで `repository.complete()` を呼ぶ（Room はメインスレッド不可のため Phase 3 から前倒し）
- 動作確認用の `SampleTasks` / `replaceWithSamples()` / 仮画面は Phase 4 で削除済み
- 確認済み：空のDBでは通知が出ない／サンプル投入で通知が出る／バラバラの順で入れても SQL で正しく並ぶ／チェックで完了し繰り上がる／force-stop 後も状態が残る

## 常駐性の実装メモ（Phase 3）
- `NotificationDismissedReceiver`（exported=false）：`setDeleteIntent` で呼ばれ、`NotificationUpdater.update()` で出し直す。アプリ自身の `cancel()` では呼ばれないので、0件で消したときに再投稿されることはない
- `NotificationRestoreReceiver`（exported=true、`RECEIVE_BOOT_COMPLETED` 権限）：`BOOT_COMPLETED` と `MY_PACKAGE_REPLACED` で出し直す。exported なので、想定外のアクションは Receiver 側で無視している
- Receiver 共通の `goAsync()` 処理は `notification/ReceiverAsync.kt` の `launchAsync()` にまとめた
- 確認済み（エミュレータ API 36）
  - スワイプで消す → すぐに再投稿される
  - 「すべて消去」→ ongoing 通知はそもそも消えない（Receiver も呼ばれない）
  - アプリ更新（`adb install -r`）→ 更新時に通知が消え、約8秒後に `MY_PACKAGE_REPLACED` で再表示
  - 端末再起動 → 起動完了から約45秒後に `BOOT_COMPLETED` が届いて再表示（届くまでの時間は端末次第）
  - 0件で再起動 → `BOOT_COMPLETED` は届くが通知は出ない
- 未確認：Android 12（API 31）での挙動。ユーザーがアプリを強制停止した場合は、次にアプリを開くまで通知は戻らない（Android の仕様で、強制停止中はブロードキャストが届かないため）

## アプリ画面の実装メモ（Phase 4）
- 構成（`com.srtodo.app`）
  - `ui/tasklist/TaskListScreen`：一覧。未完了（通知と同じ並び）→「完了済み（N）」の開閉セクション。行タップで編集、チェックで完了／未完了、完了済みの行はゴミ箱で削除。0件のときは案内文
  - `ui/tasklist/TaskEditDialog`：追加・編集ダイアログ。期限は「日付（DatePickerDialog）→ 時刻（TimePicker を AlertDialog で包む）」の順に選ぶ。未設定時の初期値は次の正時。編集時は「削除」ボタンあり
  - `ui/tasklist/TaskListViewModel`：Repository の Flow を StateFlow にして公開。`viewModelFactory` で `AppContainer` から Repository を受け取る
  - `util/DueFormatter`：期限の表示文字列（通知とアプリ画面で共通）
  - `ui/theme/Theme`：独自色は `SRTodoTheme.colors`（今は `overdue` のみ。通知と同じ `R.color.task_overdue` を使う）
- アイコンは Material Icons ライブラリを入れず、`res/drawable` のベクター（`ic_add` / `ic_delete` / `ic_expand`）を使っている
- LazyColumn のキーは未完了 `p-ID`、完了済み `c-ID`。2つの Flow が別々に更新されるため、移動の瞬間にキーが重なってクラッシュしないようにしている
- 編集中のタスクは ID で `rememberSaveable` に持つ（画面回転でダイアログが閉じない）。新規は ID=0
- 期限切れの自動更新：`NotificationUpdater.update()` のたびに、未完了で一番近い未来の期限＋1秒に `AlarmManager.set(RTC)` を登録（無ければ解除）→ `OverdueRefreshReceiver` が通知を作り直す。RTC は端末を起こさないので、スリープ中なら次に画面が点いたときに届く。権限は不要。アラームは再起動・強制停止で消えるが、`BOOT_COMPLETED` とアプリ起動時に `update()` するので登録し直される
- MainActivity は起動時（初回のみ）に `update()` する。強制停止後に開いたときに通知とアラームを戻すため
- 確認済み（エミュレータ API 36）：完了済みの表示と開閉／チェックを外して未完了に戻す（0件→1件で通知が出る）／完了済みの削除／追加（タイトル自動フォーカス、日付→時刻、期限順の位置に入る）／編集（期限を外すと赤字が消え最後尾へ）／編集ダイアログから削除／一覧で完了して0件になると通知が消える／13:15 期限のタスクで 13:15:02 にアラームが届き通知が赤字になる／期限がなくなるとアラームが解除される／ダークモード
- 未確認：タスクが1件もないときの案内文の表示（全件削除の操作はしていない）、画面回転
- 日付ピッカーの表記（曜日・月名）は端末の言語に従う。エミュレータは en-US のため英語で表示された

## 未決事項（あとで検討）
- **日付の表示**：タイムゾーンや時刻を変更したとき（`TIMEZONE_CHANGED` / `TIME_SET`）にも作り直すか

## 通知の実装メモ（Phase 1 で確認済み）
- 構成：`notification/NotificationUpdater`（Phase 1 の `TaskNotifier` から改名。通知の組み立て・投稿）、`TaskCheckReceiver`（チェック受信）
- レイアウト：`notification_task_list.xml`（枠）に `notification_task_row.xml`（1行48dp）を `addView` する。折りたたみ時1件・展開時3件で、`DecoratedCustomViewStyle` を使う
- チェック：`setOnCheckedChangeResponse` ＋ `RemoteResponse.fromPendingIntent` で Receiver に届く。チェック状態は `RemoteViews.EXTRA_CHECKED` で受け取る。fill-in されるため **PendingIntent は `FLAG_MUTABLE` 必須**（明示的 Intent なので Android 14 の制限に当たらない）。requestCode はタスクIDにして行ごとに分ける
- 折りたたみ時のチェックボックスも動作する
- 期限切れの赤字：`setColorStateList(id, "setTextColor", R.color.task_overdue)`（API 31以降）で通知側のコンテキストで色を解決させ、`values` / `values-night` でライト／ダークを切り替えている
- 文字色は `TextAppearance.Compat.Notification.Title` / `.Info` を使い、ダークモードでも自動で切り替わる
- 「残り N 件」は `setSubText` でヘッダーに出す（展開時のみ見える）
- 通知チャンネルは `IMPORTANCE_DEFAULT`（音・バイブなし）。LOW だと「サイレント」欄に入るため
- 長いタイトルは1行で末尾が「…」になる
- 気になった点（デザイナー向け）：ダークモードでは未チェックのチェックボックスの枠が暗く、目立ちにくい
- 未確認：Android 12（API 31）の実機・エミュレータでの見た目（今回は API 36 で確認）

## ビルド環境（Phase 0 で確定）
- JDK 21（Temurin）。インストールされているのは21だけなので、ツールチェーンは指定せず、`jvmTarget` と `compileOptions` で 17 を指定している
- Gradle 8.14.5 / AGP 8.13.2 / Kotlin 2.4.20 / KSP 2.3.13 / Room 2.8.5（Room Gradle Plugin でスキーマを `app/schemas` に出力）
- compileSdk / targetSdk 36（SDKに入っているのは android-36 まで）
- **Compose BOM は 2026.06.00（Compose 1.11 系）に固定**。2026.08.00 以降（Compose 1.12）は AGP 9.1 以上と compileSdk 37 が必要なため使えない。上げるときは AGP 9 系への移行と SDK 37 の導入をあわせて行うこと
- パッケージ名 / applicationId: `com.srtodo.app`
- バージョンは `gradle/libs.versions.toml` で管理している
- ビルド時の「Unable to strip libandroidx.graphics.path.so」は NDK が未導入なだけで、無視してよい

## 次にやること
Phase 5（デザインを反映）はデザインが決まるまで着手できない。それまでは、デザインに関係しない Phase 6 の一部（DAO の並び順と繰り上がりのテスト）を先に進めるのがよい
