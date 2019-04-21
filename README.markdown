# yoteibot: Microsoft Exchange Serverから予定を取得してSlackに通知するボット

([IRC版](../../tree/irc/))

このボットのいるSlackチャンネルで`yotei yamada`と発言すると、
yamadaさんの予定を返してくれるボットです
(会議室のemailアドレスを登録しておけば、会議室の予定の参照も可能です)。

```
>deton< yotei yamada
<yoteibot>
  ▼10:00-11:00 運用管理打合せ(谷川Gr)(N210)
  ●26日10:30-12:00 特許検討会
```

Outlookを起動してその人の予定を開く操作をして
表示されるのを待つ(数十秒待たされる場合あり)よりも早いと思います。

## yotei
予定を参照するコマンドです。
引数無しで実行すると、発言者のニックネームに対応する予定を返します。

日付指定無しの場合は、「実行時-2時間」から1日後までの予定を表示します
(会議が延びている場合は知りたいので-2時間)。

* 自分の予定を参照: `yotei`
* 自分の予定を参照(日付指定): `yotei kyo`
* 他人の予定を参照: `yotei yamada`
* 他人の予定を参照(日付指定): `yotei yamada asu`

* 日付指定
 * `kyo`
 * `asu`
 * `1222`
 * `20141222`

## yoteiconf
nick2email.xml(人のnickから、Exchangeでのemailアドレスへの変換表)を更新するためのコマンドです。
参照・設定・削除をします。

* 参照: `yoteiconf taro`
* 設定: `yoteiconf taro=taro@example.jp`
* 削除: `yoteiconf taro=`

### 呼び出し回避
yoteiconfやyoteiコマンドでは、
ニックネームやemail中に含まれる`,`,`:`,`;`,`/`の文字は無視するようにしています。
つまり、yoteibotは、`yotei ya,mada`という発言を、`yotei yamada`とみなします。

山田さんが`yamada`を通知キーワードに設定している場合、
他の人が`yotei yamada`と発言すると、
山田さんへの呼び出し通知が発生するので、それを回避するための機能です。

## 予定表示形式
各予定は以下の形式で表示します。

●?26日10:00-11:00 打合せ(場所)

* 予定開始マーク:
 * ▼: 今日の予定
 * ●: 今日以外の予定
* 予定状態。状態が「空き時間」の予定は表示しません。
 * ?: 仮の予定
 * bold: 外出中
 * デフォルト: それ以外
* 開始時刻。前の予定と日が変わる場合は何日かも表示。月も同様。
* 終了時刻。開始時刻と日が変わる場合は何日かも表示。
* 予定のsubject。subjectが取得できない場合(非公開の予定)は"-"。
* 予定のlocation。場所が空の場合は()ごと表示しない。

## 起動時引数
* 設定ファイルのディレクトリ(オプション。デフォルトは`$HOME/.yoteibot/`)

## 環境変数
* `SLACK_BOT_AUTH_TOKEN`: Slack接続用トークン

現状、`localhost:8888`のproxy経由でSlackに接続するコードになっています。

Slack接続は、[simple-slack-api](https://github.com/Ullink/simple-slack-api) 
を使用しています。

## 設定ファイル
* exchange.xml: Exchange Server接続情報。
* nick2email.xml: 人のnickから、Exchangeでのemailアドレスへの変換テーブル。
* location.xml: 場所文字列の短縮を行うためのデータ。キーが正規表現。値は置換後文字列($1等を使用可)
* ignore.xml: 無視したい予定の正規表現パターン。

### exchange.xml
Exchange Serverへの接続情報です。

```
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE properties SYSTEM "http://java.sun.com/dtd/properties.dtd">
<properties>
  <entry key="server">exchange.example.jp</entry>
  <entry key="userId">taro</entry>
  <entry key="password">p@sSw0rd</entry>
</properties>
```

### nick2email.xml
人のnickから、Exchangeでのemailアドレスへの変換テーブル。

`yoteiconf`コマンド発言による編集も可能。

```
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE properties SYSTEM "http://java.sun.com/dtd/properties.dtd">
<properties>
  <entry key="deton">deton@mail.example.jp</entry>
</properties>
```

### location.xml
場所を示す文字列が長すぎる場合に、短縮を行うための設定です。
キーが正規表現。値は置換後文字列($1等を使用可)。

例: "本社）川崎共通 本1 1階 A101会議室 10人"→"A101"

```
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE properties SYSTEM "http://java.sun.com/dtd/properties.dtd">
<properties>
  <entry key="^本社）川崎共通 本1 [0-9]+階 (.*)会議室.*$">$1</entry>
</properties>
```

### ignore.xml
無視したい予定の正規表現パターンの設定。
キーが正規表現。subjectがマッチしたら、その予定を無視します。
他システムとの連携用の予定などを表示したくない場合向け。

```
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE properties SYSTEM "http://java.sun.com/dtd/properties.dtd">
<properties>
  <entry key="^\[HP\] .*">1</entry>
</properties>
```

## ライセンス
GPL v3
