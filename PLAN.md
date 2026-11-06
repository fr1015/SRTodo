# 開発計画・引き継ぎメモ

最終更新: 2026-11-06
進捗: Phase 4 完了。Phase 6 のうち DAO / Repository のテストを追加済み（Phase 5 は保留）。2026-11-06 に通知をフォアグラウンドサービスで固定表示にした
デザイン: 当面は Material3 標準の仮デザインのままでよい（2026-10-31 に決定）。Phase 5 は保留

## 決定済みの仕様
| 項目 | 決定内容 |
|---|---|
| 期限なしタスク | 許可する。並び順では最後（期限ありを期限の近い順に並べ、その後ろ） |
| タスク0件のとき | 通知を消す（`cancel`）。0件のときはスワイプされても再投稿しない／再起動後も表示しない。タスクが追加されたら再表示する |
| 期限切れタスク | 通知内で赤字で表示する（アプリ画面も同じ扱いにする） |
| 期限の粒度 | 日付と時刻（日時ピッカーで設定。`dueAt: Long?` に epoch millis で保存） |
| 期限切れへの切り替わり | 期限を過ぎたら、操作がなくても通知を赤字に作り直す（`AlarmManager` の RTC アラーム。数分の遅れは許容）。アプリ画面は毎分0秒に表示を更新する |
| 通知欄での位置（2026-11-06 改定） | ~~通常の通知のまま `when` を新しくして上に戻す~~ → **色付きのフォアグラウンドサービス（`specialUse`）の通知にして、通知欄の一番上の枠に固定する**。端末の負担を最小にするため「何もしないサービス＋イベントのときだけ作り直す」にする（ループ・定期確認・WakeLock・`ACTION_TIME_TICK`・電池最適化の除外は使わない）。Play ストアには公開しない前提 |
| 固定表示の設定 | 設定画面に「通知に固定表示する」（既定オン）。オフならサービスは使わず、通常の常駐通知（作り直すたびに `when` を新しくして上に戻す方式）を出す |
| 通知の形 | 今の RemoteViews＋チェックボックスのまま（InboxStyle にはしない） |
| 期限の表記 | 絶対表記のまま（「今日」「明日」などの相対表記は一旦なし）。そのため画面点灯（`ACTION_SCREEN_ON`）での作り直しもしない。相対表記を入れるなら、サービス内で `SCREEN_ON` を動的登録して作り直す |
| 通知チャンネル | `IMPORTANCE_DEFAULT`（ID `task_list`）、音・バイブなし、`setOnlyAlertOnce(true)` |

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
- [ ] **Phase 6：仕上げ**：~~DAOの並び順と繰り上がりのテスト~~（済み）。実機で再起動、スワイプで消す、ダークモード、0件→1件の再表示、期限切れの赤字表示を確認（エミュレータでは Phase 1〜4 で確認済み。実機は未確認）

## 通知デザインの制約（デザイナー向け）
- 通知の中身は RemoteViews のため Compose は使えない。使える View が限られる（TextView, CheckBox, ImageView, LinearLayout など）
- 高さに上限がある（展開時でも約256dp）。折りたたみ時に3件すべては入らない可能性があるので、「折りたたみ時は1件、展開すると3件」を想定する
- 通知の外枠（アプリ名・アイコンの行）はシステムが描く。デザインできるのは中身の部分だけ
- ライトとダーク、両方のテーマに対応すること

## データ層の実装メモ（Phase 2）
- 構成（`com.srtodo.app`）
  - `data/Task`（エンティティ、テーブル名 `tasks`、`(isDone, dueAt)` にインデックス）、`data/TaskDao`、`data/AppDatabase`（DB名 `srtodo.db`、version 1、スキーマは `SRTodo/schemas` に出力してコミットする）
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
- 期限切れの自動更新：`NotificationUpdater.update()` のたびに、「未完了で一番近い未来の期限＋1秒」と「次の0時」の早い方に `AlarmManager.set(RTC)` を登録（タスク0件なら解除。0時は 2026-11-06 に追加。通知を新しくして通知欄の上に戻すため）→ `OverdueRefreshReceiver` が通知を作り直す。RTC は端末を起こさないので、スリープ中なら次に画面が点いたときに届く。権限は不要。アラームは再起動・強制停止で消えるが、`BOOT_COMPLETED` とアプリ起動時に `update()` するので登録し直される
- MainActivity は起動時（初回のみ）に `update()` する。強制停止後に開いたときに通知とアラームを戻すため
- 確認済み（エミュレータ API 36）：完了済みの表示と開閉／チェックを外して未完了に戻す（0件→1件で通知が出る）／完了済みの削除／追加（タイトル自動フォーカス、日付→時刻、期限順の位置に入る）／編集（期限を外すと赤字が消え最後尾へ）／編集ダイアログから削除／一覧で完了して0件になると通知が消える／13:15 期限のタスクで 13:15:02 にアラームが届き通知が赤字になる／期限がなくなるとアラームが解除される／ダークモード
- 未確認：タスクが1件もないときの案内文の表示（全件削除の操作はしていない）、画面回転
- 日付ピッカーの表記（曜日・月名）は端末の言語に従う。エミュレータは en-US のため英語で表示された

## テストの実装メモ（Phase 6）
- 実行方法
  - JVM の単体テスト：`./gradlew testDebugUnitTest`
  - エミュレータ（実機）上のテスト：`./gradlew connectedDebugAndroidTest`（端末かエミュレータを接続しておく）。**終了時にアプリがアンインストールされる**ので、続けて手で確認するときは入れ直して通知の権限を許可し直す
- `SRTodo/src/test`：`TaskTest`（期限切れの判定。期限ちょうどはまだ期限切れでない、期限なし・完了済みは期限切れにならない）
- `SRTodo/src/androidTest`：Room のインメモリ DB（実際の SQLite）で SQL を確かめる
  - `TaskDaoTest`：期限の近い順・期限なしは最後・同じ期限は作成順→ID順／上位3件／完了済みは除外／完了で4件目が繰り上がる／真ん中を完了しても崩れない／二重に完了しても完了日時を上書きしない／未完了に戻すと元の位置へ／完了済みは完了が新しい順／削除で繰り上がる／次の期限（過去・完了済み・期限なしを除く）
  - `TaskRepositoryTest`：追加・完了・未完了に戻す・編集・削除の結果と、書き込みのたびに通知の作り直しが1回呼ばれること
- Repository から通知を作り直す部分は `data/TaskChangeListener` に切り出した（本番は `NotificationUpdater` が実装）。テストでは呼ばれた回数を数える偽物を渡すので、テスト中に通知は出ない
- テストを書いていて見つけた不具合：`getNextDueAfter` が `dueAt > now` だったため、期限がちょうど現在時刻のタスクにアラームが登録されず赤字に切り替わらない可能性があった。`dueAt >= now` に修正（アラームは `dueAt + 1秒` なので、同じ時刻に登録し直し続けることはない）
- 結果（2026-10-31、Pixel_8 エミュレータ API 36）：単体 5件、エミュレータ上 23件、すべて成功
- 2026-11-06 に追加：`RefreshAlarmTest`（次に作り直す時刻。JVM）、`TaskDaoTest` に常駐サービス用の Flow（上位3件・件数）のテスト。結果：単体 10件、エミュレータ上 27件、すべて成功

## 未決事項（あとで検討）
- ~~**日付の表示**：タイムゾーンや時刻を変更したとき（`TIMEZONE_CHANGED` / `TIME_SET`）にも作り直すか~~ → 2026-11-06 に対応済み
- **相対表記**（「今日」「明日」）を入れるか。入れる場合は画面点灯での作り直しもあわせて入れる

## 通知の実装メモ（Phase 1 で確認済み）
- 構成：`notification/NotificationUpdater`（Phase 1 の `TaskNotifier` から改名。通知の組み立て・投稿）、`TaskCheckReceiver`（チェック受信）
- レイアウト：`notification_task_list.xml`（枠）に `notification_task_row.xml`（1行48dp）を `addView` する。折りたたみ時1件・展開時3件で、`DecoratedCustomViewStyle` を使う
- チェック：`setOnCheckedChangeResponse` ＋ `RemoteResponse.fromPendingIntent` で Receiver に届く。チェック状態は `RemoteViews.EXTRA_CHECKED` で受け取る。fill-in されるため **PendingIntent は `FLAG_MUTABLE` 必須**（明示的 Intent なので Android 14 の制限に当たらない）。requestCode はタスクIDにして行ごとに分ける
- 折りたたみ時のチェックボックスも動作する
- 期限切れの赤字：`setColorStateList(id, "setTextColor", R.color.task_overdue)`（API 31以降）で通知側のコンテキストで色を解決させ、`values` / `values-night` でライト／ダークを切り替えている
- 文字色は `TextAppearance.Compat.Notification.Title` / `.Info` を使い、ダークモードでも自動で切り替わる
- 「残り N 件」は `setSubText` でヘッダーに出す（展開時のみ見える）
- 通知チャンネルは `IMPORTANCE_DEFAULT`（ID `task_list`、音・バイブなし）。LOW だと「サイレント」欄に入るため DEFAULT 以上にする。一時期 HIGH（ID `tasks_top`）にしたが、並び順に効果がなかったため DEFAULT に戻した。旧チャンネル `tasks` / `tasks_top` は `createChannel()` で削除している
- 通知欄の並び順（2026-11-06 に API 36 で調査）
  - 重要度を HIGH にしても、後から来た DEFAULT の通知の下に表示された。最近の Android は重要度を「DEFAULT 以上か」でしか比べず、その中では新しい順に並ぶため
  - 常に最上部に固定できるのは、通話・メディア・会話・色付きフォアグラウンドサービスの通知だけ。→ 色付きフォアグラウンドサービスを採用した（「固定表示の実装メモ」を参照）
  - 通知を作り直す（`notify()` で更新する、またはスワイプ後に再投稿する）と `when` が新しくなり、一番上に戻ることを確認した。`setWhen(now)` を明示している
- 長いタイトルは1行で末尾が「…」になる
- 気になった点（デザイナー向け）：ダークモードでは未チェックのチェックボックスの枠が暗く、目立ちにくい
- 未確認：Android 12（API 31）の実機・エミュレータでの見た目（今回は API 36 で確認）

## 固定表示（フォアグラウンドサービス）の実装メモ（2026-11-06）
- 構成（`com.srtodo.app`）
  - `notification/TaskNotificationService`：`startForeground()` で通知を持って待つだけ。戻り値は `START_STICKY`。`observePendingTop(3)` と `observePendingCount()` を `distinctUntilChanged()` して collect し、変化したときだけ同じ ID で `notify()`。0件になったら `stopForeground(STOP_FOREGROUND_REMOVE)` と `stopSelf()`。設定がオフになったら `STOP_FOREGROUND_DETACH` で抜けて通常の通知に切り替える
  - `notification/TaskNotificationController`：窓口。Receiver・画面・Repository（`TaskChangeListener`）はここだけを呼ぶ。サービスが動いていれば `refreshTicks` を進めて作り直しを頼み、止まっていれば開始する（設定オフなら `NotificationUpdater`）。`ForegroundServiceStartNotAllowedException` を捕捉し、開始できなければ通常の通知で代用する
  - `notification/TaskNotificationBuilder`：通知の組み立てだけ（固定表示・通常で共通）。固定表示は `setColorized(true)`＋`setColor(R.color.notification_pinned)`＋`FOREGROUND_SERVICE_IMMEDIATE`
  - `notification/NotificationUpdater`：通常の常駐通知（設定オフのときと、サービスを開始できなかったとき）
  - `notification/RefreshAlarm`：「次の期限＋1秒」と「次の0時」の早い方に RTC の `set()` を1本だけ予約（正確なアラームの権限は使わない）
  - `settings/AppSettings`（SharedPreferences）、`ui/settings/SettingsScreen`（一覧画面の右上の歯車から開く。画面の切り替えは `MainActivity` の状態で行う）
- Manifest：`FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE`、サービスは `exported="false"`・同じプロセス・`foregroundServiceType="specialUse"`・`PROPERTY_SPECIAL_USE_FGS_SUBTYPE` に用途を記載。`NotificationRestoreReceiver` に `TIME_SET` / `TIMEZONE_CHANGED` を追加（未決事項だった時刻変更時の作り直しはこれで解消）
- `startForeground()` の種類は Android 14 以降だけ `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` を渡し、それより前は 0
- 最初の通知は `onStartCommand` 内で `runBlocking(Dispatchers.IO)` で作る（`startForegroundService()` の数秒以内に `startForeground()` が必要なため。読むのは3件と件数だけ）
- 色付きの背景はライト／ダークで変わらないので、固定表示のときは文字色（白・薄い青・期限切れは薄い赤 `#FFB4AB`）とチェックボックスの色を直接指定している
- バックグラウンドからの開始（公式ドキュメントで確認、2026-11-06）
  - Android 15 で `BOOT_COMPLETED` から開始できない種類は `dataSync` / `camera` / `mediaPlayback` / `phoneCall` / `mediaProjection` / `microphone`。**`specialUse` は対象外**（https://developer.android.com/about/versions/15/behavior-changes-15#fgs-boot-completed ）
  - `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` / `TIME_SET` / `TIMEZONE_CHANGED` の受信時と通知の操作は、バックグラウンドから開始してよい例外。**正確でないアラームは例外に入らない**（https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start ）。アラームはサービスが動いている前提で作り直しを頼むだけにし、止まっていて開始できなければ通常の通知で代用する
  - タスクを0件→1件にできるのはアプリ画面（前面）だけなので、再開は制限に当たらない
- `Application.onCreate` は `AppContainer` の作成（中身はすべて `by lazy`）とチャンネル作成だけで、重い初期化はない（遅延初期化の提案なし）
- 確認済み（エミュレータ API 36）
  - 固定表示の通知は、後から来た他アプリの通知より上の別の枠に表示される（ライト／ダークとも文字が読める）
  - アプリ画面での追加、通知のチェックでの完了がすぐ反映される（繰り上がる）
  - スワイプで消す → すぐ出し直される（2回確認）
  - アプリ更新（`MY_PACKAGE_REPLACED`）・端末再起動（`BOOT_COMPLETED`、起動完了から約25秒）でサービスが例外なく再開する
  - 時刻を進める（`cmd alarm set-time`）→ `TIME_SET` で作り直され、期限切れが赤字になり、アラームが次の0時に予約し直される
  - 0件 → サービス停止・通知とアラームが消える。追加 → 再開
  - 設定オフ → サービスが止まり通常の通知に切り替わる。オン → 固定表示に戻る
- 未確認：0時のアラームが実際に届くところ（`set()` は最大1時間ほど遅れることがある）、Android 12〜15 での動作（エミュレータは API 36 のみ）

### 実機での確認手順
1. アプリを入れて開き、通知を許可する。タスクを3件以上追加し、通知欄の一番上に色付きの通知が出ることを確認する
2. **即時更新**：アプリでタスクを追加・編集・削除し、通知がすぐ変わることを確認する。通知のチェックボックスで完了し、次のタスクが繰り上がることを確認する
3. **他アプリの通知より上**：別のアプリから通知を受ける（自分にメッセージを送るなど）。SRTodo の通知が一番上のままであることを確認する。会話（メッセージ）の通知や着信の通知と並んだときの順番も見ておく
4. **スワイプで復帰**：通知を横にスワイプして消す。すぐに出し直されることを確認する（Android 13 以降は消せる）
5. **再起動で復帰**：端末を再起動し、ロック解除後しばらく（〜1分程度）で通知が戻ることを確認する
6. **日付をまたぐ**：期限を「今日の数分後」にしたタスクを作り、期限を過ぎたら赤字になることを確認する。日付の切り替わりは、設定で自動の日付をオフにして日付を進め、作り直されることを確認する（作業後は自動に戻す）。0時ちょうどのアラームは最大1時間ほど遅れることがある
7. **設定**：設定画面で「通知に固定表示する」をオフにして、通常の通知（色なし）になることを確認する。オンに戻すと固定表示に戻ることを確認する
8. **0件**：すべて完了して通知が消えることを確認する。設定アプリの「実行中のアプリ」（または通知欄下部のアクティブなアプリ）から SRTodo が消えることも確認する
9. **Android 14 / 15**：上の 1〜8 を Android 14 と 15 の端末でそれぞれ行う。特に Android 15 では再起動（手順5）で `ForegroundServiceStartNotAllowedException` が出ないこと（`adb logcat -s TaskNotification TaskNotificationService` に警告が出ないこと）を確認する
10. **電池・負担**：一晩置いて、設定 > 電池 で SRTodo の使用量がほぼ0であることを確認する

## ビルド環境（Phase 0 で確定）
- JDK 21（Temurin）。インストールされているのは21だけなので、ツールチェーンは指定せず、`jvmTarget` と `compileOptions` で 17 を指定している
- Gradle 8.14.5 / AGP 8.13.2 / Kotlin 2.4.20 / KSP 2.3.13 / Room 2.8.5（Room Gradle Plugin でスキーマを `SRTodo/schemas` に出力）
- compileSdk / targetSdk 36（SDKに入っているのは android-36 まで）
- **Compose BOM は 2026.06.00（Compose 1.11 系）に固定**。2026.08.00 以降（Compose 1.12）は AGP 9.1 以上と compileSdk 37 が必要なため使えない。上げるときは AGP 9 系への移行と SDK 37 の導入をあわせて行うこと
- パッケージ名 / applicationId: `com.srtodo.app`
- アプリのモジュール（フォルダ）名は `SRTodo`（2026-11-06 に `app` から変更。`settings.gradle.kts` は `include(":SRTodo")`）。Gradle タスクは `:SRTodo:assembleDebug` など
- バージョンは `gradle/libs.versions.toml` で管理している
- ビルド時の「Unable to strip libandroidx.graphics.path.so」は NDK が未導入なだけで、無視してよい

## 次にやること
Phase 6 の残り：実機での確認（「固定表示の実装メモ」の実機での確認手順。Android 14 / 15 を含む）と、エミュレータで未確認だった項目（Android 12 / API 31 での見た目と挙動、画面回転、タスク0件の案内表示）。Phase 5（デザインの反映）はデザインが用意できたら着手する
