import java.io.*;
import java.net.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.logging.*;
import microsoft.exchange.webservices.data.*;
import org.pircbotx.*;
import org.pircbotx.hooks.*;
import org.pircbotx.hooks.events.*;
import org.pircbotx.hooks.types.*;

public class IrcBotForExchangeAppointment extends ListenerAdapter {
    static Logger logger = Logger.getLogger("IrcBotForExchangeAppointment");
    ExchangeClient exchange = new ExchangeClient();

    final static String server = LocalProperties.server;
    final static String userId = LocalProperties.userId;
    final static String password = LocalProperties.password;
    final static String ircServer = LocalProperties.ircServer;
    final static String ircChannel = LocalProperties.ircChannel;

    Properties botnick2usernick;
    Properties nick2email;

    public IrcBotForExchangeAppointment() {
        botnick2usernick = loadConfigurationFile("botnick2usernick.properties");
        nick2email = loadConfigurationFile("nick2email.properties");
    }

    @Override
    public void onMessage(MessageEvent event) throws Exception {
        String nick = event.getUser().getNick();
        String msg = event.getMessage();
        String respmsg = null;
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "onMessage: " + msg);
        }
        try {
            if (isBotNick(nick)) {
                respmsg = getAppointment(getEmailAddressFromNick(getUserNickFromBotNick(nick)));
            } else if (msg.startsWith("yotei")) {
                // TODO: "予定"や"よてい"等にも反応する
                respmsg = handleYoteiMessage(nick, msg);
                // TODO: botnick→usernickやusernick→emailaddress変換テーブルを
                // ボット用コマンド発言をもとに動的に登録。
                // 例: "yotei botnick detonPHS deton"や、
                // "yotei email deton@example.com deton"
            }
        } catch (Exception ex) {
            if (logger.isLoggable(Level.INFO)) {
                logger.log(Level.INFO, "onMessage", ex);
            }
            respmsg = ex.getMessage();
        }
        if (respmsg == null || respmsg.length() == 0) {
            respmsg = "予定無し";
        }
        //event.respond(respmsg); // PRIVMSG
        event.getChannel().send().notice(respmsg);
    }

    public static void main(String[] args) throws Exception {
        Configuration configuration = new Configuration.Builder()
            .setName("ExchAppt") // nick of the bot
            .setServerHostname(ircServer)
            .addAutoJoinChannel(ircChannel)
            .setEncoding(Charset.forName("ISO-2022-JP"))
            .addListener(new IrcBotForExchangeAppointment())
            .buildConfiguration();

        PircBotX bot = new PircBotX(configuration);
        bot.startBot();
    }

    boolean isBotNick(String nick) {
        String usernick = botnick2usernick.getProperty(nick);
        if (usernick == null) {
            return false;
        }
        return true;
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
        String[] params = message.split("[\\s]+");
        // assert params[0].equals("yotei")
        if (params.length == 1) { // only "yotei"
            nick = fromNick;
        } else {
            nick = params[1];
        }
        //TODO: 日付等を指定するパラメータへの対応。"asu"等
        return getAppointment(getEmailAddressFromNick(nick));
    }

    /**
     * PhsRingNotifyデバイスbotのnickから、対応するユーザのnickを得る。
     * 例: "detonPHS"→"deton"
     */
    String getUserNickFromBotNick(String botnick) throws UnknownBotNickException {
        String usernick = botnick2usernick.getProperty(botnick);
        if (usernick == null) {
            throw new UnknownBotNickException("Unknown bot nick: " + botnick + ". Please add the nick to botnick2usernick.properties file.");
        }
        return usernick;
    }

    String getEmailAddressFromNick(String nick) throws UnknownUserNickException {
        String email = nick2email.getProperty(nick);
        if (email == null) {
            throw new UnknownUserNickException("Unknown user nick: " + nick + ". Please add the nick to nick2email.properties file.");
        }
        return email;
    }

    String getAppointment(String email) throws ServiceLocalException {
        if (email == null) {
            return "Unknown nick. Please register nick in configuration file.";
        }
        // 明日の予定まで取得。出張中の場合、明日は出社するかどうか聞かれた時用
        // TODO: 次の営業日まで
        long now = System.currentTimeMillis();
        final long oneDayMs = 24 * 60 * 60 * 1000;
        Date startDate = new Date(now);
        Date endDate = new Date(now + oneDayMs);
        Collection<CalendarEvent> calendarEvents;
        try {
            calendarEvents = exchange.getCalendarEvents(server, userId, password, email, startDate, endDate);
        } catch (Exception ex) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING, "getCalendarEvents", ex);
            }
            return "Failed to get appointments from Exchange: " + ex.getMessage();
        }

        StringBuilder sb = new StringBuilder();
        java.util.Formatter fmt = new java.util.Formatter(sb);
        for (CalendarEvent a : calendarEvents) {
            Date start = a.getStartTime();
            Date end = a.getEndTime();
            String subj = null;
            String loc = null;
            CalendarEventDetails details = a.getDetails();
            if (details != null) {
                subj = details.getSubject();
                loc = details.getLocation();
            }
            if (subj == null) {
                subj = "-";
            }
            if (loc == null) {
                loc = "-";
            }
            fmt.format("%1$tH:%1$tM-%2$tH:%2$tM %3$s(%4$s); ", start, end, subj, loc);
        }
        return sb.toString();
    }

    public static Properties loadConfigurationFile(String filename) {
        Properties p = new Properties();
        ClassLoader loader = IrcBotForExchangeAppointment.class.getClassLoader();
        URL url = null;
        if (loader != null) {
            url = loader.getResource(filename);
        }
        if (url == null) {
            url = ClassLoader.getSystemResource(filename);
        }
        if (url == null) {
            if (logger.isLoggable(Level.INFO)) {
                logger.info("properties file not found: " + filename);
            }
            return p;
        }

        try (InputStream in = url.openStream()) {
            p.load(in);
        } catch (IOException e) {
            if (logger.isLoggable(Level.INFO)) {
                logger.info("NG loading properties file: " + filename);
            }
        }
        return p;
    }
}
