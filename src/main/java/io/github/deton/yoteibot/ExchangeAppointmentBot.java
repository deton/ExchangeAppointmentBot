package io.github.deton.yoteibot;

import com.ullink.slack.simpleslackapi.SlackChannel;
import com.ullink.slack.simpleslackapi.SlackSession;
import com.ullink.slack.simpleslackapi.events.SlackMessagePosted;
import com.ullink.slack.simpleslackapi.impl.SlackSessionFactory;
import com.ullink.slack.simpleslackapi.listeners.SlackMessagePostedListener;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Proxy;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Properties;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.MatchResult;

import microsoft.exchange.webservices.data.core.exception.service.local.ServiceLocalException;
import microsoft.exchange.webservices.data.property.complex.availability.CalendarEvent;

public class ExchangeAppointmentBot implements SlackMessagePostedListener {
    static final String NICK2EMAIL_FILE = "nick2email.xml";
    static Logger logger = Logger.getLogger("ExchangeAppointmentBot");
    ExchangeClient exchange;
    ResponseMessageFormatter respformatter;

    File datadir;
    Properties nick2email;
    Properties locationProp;
    Properties ignoreProp;

    public ExchangeAppointmentBot(String path) throws IllegalArgumentException, IOException {
        if (path != null) {
            datadir = new File(path);
        } else {
            datadir = new File(System.getProperty("user.home"), ".yoteibot");
        }
        Properties p = loadConfigurationFile("exchange.xml");
        exchange = new ExchangeClient(p.getProperty("server"),
                p.getProperty("userId"), p.getProperty("password"));

        // load optional configuration files
        try {
            nick2email = loadConfigurationFile(NICK2EMAIL_FILE);
        } catch (IOException ex) {
            nick2email = new Properties();
        }
        try {
            locationProp = loadConfigurationFile("location.xml");
        } catch (IOException ex) {
            locationProp = new Properties();
        }
        try {
            ignoreProp = loadConfigurationFile("ignore.xml");
        } catch (IOException ex) {
            ignoreProp = new Properties();
        }

        respformatter = new ResponseMessageFormatter(locationProp, ignoreProp);
    }

    @Override
    public void onEvent(SlackMessagePosted event, SlackSession session) {
        SlackChannel channel = event.getChannel();
        String respmsg = null;
        try {
            respmsg = handleMessage(event);
        } catch (Exception e) {
            if (logger.isLoggable(Level.INFO)) {
                logger.info(e.getMessage());
            }
        }
        if (respmsg != null && respmsg.length() > 0) {
            session.sendMessage(channel, respmsg);
        }
    }

    String handleMessage(SlackMessagePosted event) throws Exception {
        String msg = event.getMessageContent();
        String nick = event.getSender().getUserName();
        String respmsg = null;
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "handleMessage: " + msg);
        }
        try {
            if (msg.startsWith("yoteiconf")) {
                respmsg = handleYoteiConfMessage(nick, removeNoiseChars(msg));
            } else if (msg.startsWith("yotei")) {
                // TODO: "予定"や"よてい"等にも反応する
                respmsg = handleYoteiMessage(nick, removeNoiseChars(msg));
            } else {
                return null;
            }
        } catch (Exception ex) {
            if (logger.isLoggable(Level.INFO)) {
                logger.log(Level.INFO, "handleMessage", ex);
            }
            respmsg = ex.getMessage();
        }
        return respmsg;
    }

    public static void main(String[] args) throws Exception {
        String datadir = null;
        if (args.length > 0) {
            datadir = args[0];
        }

        SlackSession session = SlackSessionFactory.getSlackSessionBuilder(
                System.getenv("SLACK_BOT_AUTH_TOKEN"))
                .withProxy(Proxy.Type.HTTP, "localhost", 8888)
                .build();
        session.connect();
        session.addMessagePostedListener(new ExchangeAppointmentBot(datadir));
    }

    /**
     * ニックネーム→emailアドレス変換表を設定
     * 例: 設定: "yoteiconf taro=taro@example.com"
     *     取得: "yoteiconf taro"
     *     削除: "yoteiconf taro="
     */
    String handleYoteiConfMessage(String fromNick, String message) throws ServiceLocalException, UnknownUserNickException {
        String[] params = message.split("[\\s]+");
        // assert params[0].equals("yoteiconf")
        if (params.length == 1) { // only "yoteiconf"
            return "Usage: yoteiconf taro=taro@example.com";
        }
        int i = params[1].indexOf('=');
        if (i < 0) { // 取得
            return params[1] + "=" + getEmailAddressFromNick(params[1]);
        }
        String nick = params[1].substring(0, i);
        String email = params[1].substring(i + 1);
        if (isDateParam(nick) || nick.equals("help")) {
            return "文字列(" + nick + ")は予約語のため、設定や削除不可";
        }
        if (email.length() == 0) { // 削除
            return deleteEmailAddressForNick(nick);
        }
        // TODO: channelごとにnick->email表を管理。nick文字列がぶつかってもOKに
        // XXX: 上書きする場合は、yoteiconf!のように指定してもらう
        return setEmailAddressForNick(nick, email);
    }

    /**
     * 誰かの予定を問い合わせる発言に対する応答メッセージを作る。
     * 例: "yotei yamada asu"
     * @param fromNick 発言者のnick
     * @param message 発言内容
     * @return 応答メッセージ。応答しない場合はnull。
     * @exception ServiceLocalException EWS API呼び出し時のException
     */
    String handleYoteiMessage(String fromNick, String message) throws ServiceLocalException, UnknownUserNickException {
        String nick = null;
        String date = null;
        String[] params = message.split("[\\s]+");
        // assert params[0].equals("yotei")
        if (params.length == 1) { // only "yotei"
            nick = fromNick;
        } else {
            nick = params[1];
            if (isDateParam(nick)) { // nick無しでasu等が指定された場合
                date = nick;
                nick = fromNick;
            } else if (nick.equals("help")) {
                return "Usage: yotei [nick] [asu|kyo|20141215|1215]";
            } else if (params.length >= 3) {
                // 日付等を指定するパラメータ。"asu"等
                date = params[2];
            }
        }
        // XXX: 発言者nickは大文字小文字無視したい。当面はnick->email登録で対処
        return getAppointment(getEmailAddressFromNick(nick), date);
    }

    boolean isDateParam(String param) {
        return (param.equals("asu") || param.equals("kyo") || param.matches("^[0-9]+$"));
    }

    /**
     * 呼び出し回避用にニックネーム中に入れられた余分な文字を削除する。
     * 例: "yotei yam,ada" -> "yotei yamada"
     * 例: "yoteiconf yam,ada=ya,mada@example.com"
     *     -> "yoteiconf yamada=yamada@example.com"
     */
    String removeNoiseChars(String msg) {
        return msg.replaceAll("[,:;/]", "");
    }

    String getEmailAddressFromNick(String nick) throws UnknownUserNickException {
        String email = nick2email.getProperty(nick);
        if (email == null) {
            throw new UnknownUserNickException("Unknown user nick: " + nick + ". Please add the nick: yoteiconf " + nick + "=user@example.com");
        }
        return email;
    }

    String setEmailAddressForNick(String nick, String email) {
        nick2email.setProperty(nick, email);
        String msg = "nick->email設定を登録: " + nick + "->" + email;
        try {
            saveConfigurationFile(NICK2EMAIL_FILE, nick2email);
        } catch (IOException ex) {
            return msg + "。設定ファイル(" + NICK2EMAIL_FILE + ")保存エラー: " + ex.getMessage();
        }
        return msg;
    }

    String deleteEmailAddressForNick(String nick) {
        nick2email.remove(nick);
        String msg = "nick->email設定を削除: " + nick;
        try {
            saveConfigurationFile(NICK2EMAIL_FILE, nick2email);
        } catch (IOException ex) {
            return msg + "。設定ファイル(" + NICK2EMAIL_FILE + ")保存エラー: " + ex.getMessage();
        }
        return msg;
    }

    String getAppointment(String email) throws ServiceLocalException {
        // 明日の予定まで取得。出張中の場合、明日は出社するかどうか聞かれた時用
        // TODO: 次の営業日まで
        // XXX: getCalendarEventsの場合、1日ぶんだと当日分のみ全て取得
        // XXX: 24時間未満だとException
        long now = System.currentTimeMillis();
        final long oneDayMs = 2 * 24 * 60 * 60 * 1000;
        Date startDate = new Date(now);
        Date endDate = new Date(now + oneDayMs);
        Collection<CalendarEvent> calendarEvents;
        try {
            calendarEvents = exchange.getCalendarEvents(email, startDate, endDate);
        } catch (Exception ex) {
            return "Failed to get appointments from Exchange: " + ex.getMessage();
        }
        if (calendarEvents == null) {
            return String.format("Exchangeからの予定取得失敗。時間を置いて再度試してみてください。(%s)", email);
        }
        // 終了予定後、2時間経過している予定は無視。
        // 終わらず続いている場合は知りたい。
        String respmsg = respformatter.format(calendarEvents, now - 2 * 60 * 60 * 1000);
        if (respmsg == null || respmsg.length() == 0) {
            return String.format("予定無し(%s)", email);
        }
        return respmsg;
    }

    String getAppointment(String email, String date) throws ServiceLocalException {
        if (date == null) {
            return getAppointment(email);
        }
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        if (date.equals("asu")) {
            cal.add(Calendar.DATE, 1);
        } else if (date.startsWith("kyo")) {
            // 日付指定無しだと明日の予定も表示するので、
            // 今日の予定のみ表示したい場合向け
        } else {
            Scanner s = null;
            try {
                s = new Scanner(date);
                // yyyymmdd, yyyy-mm-dd, yyyy/mm/dd
                // XXX: 19→1月9日とみなされる
                s.findInLine("(?:(\\d{4}))?\\D*(\\d{1,2})\\D*(\\d{1,2})");
                MatchResult result = s.match();
                String y = result.group(1);
                if (y != null) {
                    cal.set(Calendar.YEAR, Integer.parseInt(y));
                }
                int month = Integer.parseInt(result.group(2));
                if (y == null && month < cal.get(Calendar.MONTH) + 1) {
                    cal.add(Calendar.YEAR, 1);
                }
                int day = Integer.parseInt(result.group(3));
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "YMD:" + cal.get(Calendar.YEAR) + "-" + month + "-" + day);
                }
                cal.set(Calendar.MONTH, month - 1);
                cal.set(Calendar.DATE, day);
            } catch (IllegalStateException ex) {
                return "Date parse error (" + date + "): " + ex.getMessage();
            } finally {
                if (s != null) {
                    s.close();
                }
            }
        }
        Date startDate = cal.getTime();
        cal.add(Calendar.DATE, 1);
        Date endDate = cal.getTime();
        Collection<CalendarEvent> calendarEvents;
        try {
            calendarEvents = exchange.getCalendarEvents(email, startDate, endDate);
        } catch (Exception ex) {
            return "Failed to get appointments from Exchange: " + ex.getMessage();
        }
        if (calendarEvents == null) {
            return String.format("Exchangeからの予定取得失敗。時間を置いて再度試してみてください。(%tF, %s)", startDate, email);
        }
        String respmsg = respformatter.format(calendarEvents, 0);
        if (respmsg == null || respmsg.length() == 0) {
            return String.format("予定無し(%tF, %s)", startDate, email);
        }
        return respmsg;
    }

    Properties loadConfigurationFile(String filename) throws IOException {
        Properties p = new Properties();
        try (FileInputStream in = new FileInputStream(new File(datadir, filename))) {
            // 場所文字列の短縮用正規表現ではkeyにスペースを含めたいので、
            // propertiesファイルでなくXMLファイル
            p.loadFromXML(in);
        } catch (IOException e) {
            if (logger.isLoggable(Level.INFO)) {
                logger.info("NG loading properties file: " + filename);
            }
            throw e;
        }
        return p;
    }

    void saveConfigurationFile(String filename, Properties p) throws IOException {
        try (FileOutputStream out = new FileOutputStream(new File(datadir, filename))) {
            p.storeToXML(out, "ExchangeAppointmentBot: " + filename);
        } catch (IOException e) {
            if (logger.isLoggable(Level.INFO)) {
                logger.info("NG storing properties file: " + filename);
             }
             throw e;
        }
    }
}
