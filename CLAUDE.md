# 通知欄ToDoアプリ
- Kotlin / Jetpack Compose / Room、minSdk 31
- 未完了タスクを期限の近い順に上位3件、1つの常駐通知に表示する
- 通知内のチェックボックス（RemoteViews）で完了でき、次のタスクが繰り上がる
- 通知がスワイプで消されたら setDeleteIntent で再投稿、再起動後も BOOT_COMPLETED で再表示
- 変更後は ./gradlew assembleDebug でビルドが通ることを確認すること
- コミットメッセージやPR、コメントは日本語で書くこと
- 開発計画、決定済みの仕様、進捗は PLAN.md を参照すること。作業が進んだら PLAN.md も更新すること