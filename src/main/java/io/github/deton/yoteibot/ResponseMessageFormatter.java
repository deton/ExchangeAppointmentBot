package io.github.deton.yoteibot;

import java.util.*;
import java.util.regex.*;
import microsoft.exchange.webservices.data.*;

public class ResponseMessageFormatter {
    Properties prop;
    public ResponseMessageFormatter(Properties prop) {
        this.prop = prop;
    }

    /**
     * IRCメッセージ用に予定リストを整形する
     * @param calendarEvents 予定リスト
     * @param fromMillis 終了日時がこの日時より前の予定は除く
     */
    public String format(Collection<CalendarEvent> calendarEvents, long fromMillis) {
        long now = System.currentTimeMillis();
        StringBuilder sb = new StringBuilder();
        Formatter fmt = new Formatter(sb);
        Calendar cal = Calendar.getInstance();
        int today = cal.get(Calendar.DATE);
        int prevDate = today;
        String headmark = "▼";
        for (CalendarEvent a : calendarEvents) {
            Date start = a.getStartTime();
            Date end = a.getEndTime();
            if (end.getTime() < fromMillis) {
                continue;
            }
            cal.setTime(start);
            int date = cal.get(Calendar.DATE);
            if (date != prevDate) {
                if (date == today) {
                    // 日全体の予定があって翌日終了のため●になったのを戻す
                    headmark = "▼";
                } else {
                    headmark = "●";
                }
                fmt.format("%s%td日", headmark, start);
                prevDate = date;
            } else {
                sb.append(headmark);
            }

            boolean needResetColor = false;
            switch (a.getFreeBusyStatus()) {
            case Tentative: // 仮の予定
                sb.append("?");
                break;
            case Free: // 空き時間
                sb.append("\00315"); // light grey
                needResetColor = true;
                break;
            case OOF: // 外出中
                sb.append("\00306"); // purple
                needResetColor = true;
                break;
            }

            fmt.format("%tR", start);
            sb.append("-");
            cal.setTime(end);
            date = cal.get(Calendar.DATE);
            if (date != prevDate) {
                fmt.format("%td日", end);
                if (date == today) {
                    headmark = "▼";
                } else {
                    headmark = "●";
                }
                prevDate = date;
            }
            fmt.format("%tR", end);

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
            sb.append(" ");
            sb.append(subj);
            if (loc != null) {
                fmt.format("(%s)", shortenLocation(loc));
            }
            if (needResetColor) {
                sb.append("\003");
            }
        }
        return sb.toString();
    }

    /**
     * 会議室名を短くする。
     * 例: "本社)川崎共通 本1 1階 A101会議室 10人"→"A101"
     */
    String shortenLocation(String location) {
        StringBuffer sb = new StringBuffer();
        boolean matched = false;
        // XXX: propertiesファイルの場合、keyにスペースを含められない
        for (String key : prop.stringPropertyNames()) {
            Pattern pat = Pattern.compile(key); // TODO: compileしたものを保持
            Matcher m = pat.matcher(location);
            if (m.find()) {
                m.appendReplacement(sb, prop.getProperty(key));
                m.appendTail(sb);
                matched = true;
                break;
            }
        }
        if (matched) {
            return sb.toString();
        }
        return location;
    }
}
