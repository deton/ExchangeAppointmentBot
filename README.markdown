# yoteibot: Microsoft Exchange Serverから予定を取得してIRCに通知するボット

このボットのいるIRCチャンネルで`yotei yamada`と発言すると、
yamadaさんの予定を返してくれるボットです
(会議室のemailアドレスを登録しておけば、会議室の予定の参照も可能です)。

Outlookを起動してその人の予定を開く操作をして
表示されるのを待つ(数十秒待たされる場合あり)よりも早いと思います。

さらに、[PHS着信電波を検出してIRCに通知するボット](https://github.com/deton/phsringnotify)
がチャンネルに発言する着信通知メッセージを受けて、
該当する人の予定を発言します。

会社の事務所で、近くの人の構内PHSが鳴っているが、その人が離席中の場合に、
かわりに電話をとった時、席をはずしている理由を知りたいので
(休み/出張/会議、会議がいつ終わるのか、出張からいつ帰ってくるのか、
明日はいるのか、などを聞かれた場合、予定表を参照する必要があるので)。

<!--
PhsRingNotifyデバイスが、PHS着信時にIRCに発言する通知メッセージを受けて、
該当する人の予定をExchange Serverから取得して、IRCに流すボットです。
-->

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

<!--nickは、yoteiコマンドを引数無しで実行した場合は、発言者のニックネーム。-->

(yoteiコマンドの引数でnickが指定された場合は、
任意の文字列(使える文字はIRCのニックネームと同じ)。
なので、IRCのニックネームと合わせる必要はありません。
例えば、IRCのニックネームがyamada4の人のnickとして、yamadaをyoteibotに登録しておく等。)

### 呼び出し回避
yoteiconfやyoteiコマンドで、他人のニックネームを指定する場合に、
ニックネームをそのまま指定すると、
その人のクライアント側でそのキーワードが設定されている場合、
その人を呼び出す形になってしまいます。

例えば、山田さんがIRCクライアントで`yamada`をキーワードに設定している場合、
他の人が`yotei yamada`と発言すると、
山田さんのIRCクライアントは呼び出されたとみなして、山田さんに通知。

これを回避できるように、
ニックネームやemail中に含まれる`,`,`:`,`;`,`/`の文字は無視するようにしています。
つまり、yoteibotは、`yotei ya,mada`という発言を、`yotei yamada`とみなします。

<!--
呼び出しにならないようにするため、
`yotei ya,mada`のように、
ニックネームやemail中に`,`,`:`,`;`,`/`の文字を含められるようにしています。
-->

## 起動時引数
* IRCサーバホスト名。例: `irc.example.com`
* botのニックネーム。例: `[yotei]`
* JOINするチャンネル。例: `#projA`
* 設定ファイルのディレクトリ(オプション。デフォルトは`$HOME/.yoteibot/`)

## 設定ファイル
* exchange.xml: Exchange Server接続情報。
* botnick2usernick.xml: PHS着信通知ボットのニックネームから、人のnickへの変換テーブル
* nick2email.xml: 人のnickから、Exchangeでのemailアドレスへの変換テーブル。
* location.xml: 場所文字列の短縮を行うためのデータ。キーが正規表現。値は置換後文字列($1等を使用可)

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

### botnick2usernick.xml
PHS着信通知ボットのニックネームから、人のnickへの変換テーブル。

```
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE properties SYSTEM "http://java.sun.com/dtd/properties.dtd">
<properties>
  <entry key="[PHSdeto]">deton</entry>
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

## ライセンス
使用しているIRCライブラリのPircBotXがGPL v3なので、yoteibotもGPL v3です。
