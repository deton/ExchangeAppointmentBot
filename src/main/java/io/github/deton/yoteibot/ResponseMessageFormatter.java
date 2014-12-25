package io.github.deton.yoteibot;

import java.util.*;
import java.util.regex.*;
import microsoft.exchange.webservices.data.*;

public class ResponseMessageFormatter {
    Properties prop;
    Properties ignoreProp;

    public ResponseMessageFormatter(Properties prop, Properties ignoreProp) {
        this.prop = prop;
        this.ignoreProp = ignoreProp;
    }

    /**
     * IRCメッセージ用に予定リストを整形する
     * @param calendarEvents 予定リスト
     * @param fromMillis 終了日時がこの日時より前の予定は除く
     */
    public String format(Collection<CalendarEvent> calendarEvents, long fromMillis) {
        if (calendarEvents == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        StringBuilder sb = new StringBuilder();
        Formatter fmt = new Formatter(sb);
        Calendar cal = Calendar.getInstance();
        int today = cal.get(Calendar.DATE);
        int todayMonth = cal.get(Calendar.MONTH);
        int prevDate = today;
        int prevMonth = todayMonth;
        String headmark = "▼";
        for (CalendarEvent a : calendarEvents) {
            Date start = a.getStartTime();
            Date end = a.getEndTime();
            if (end.getTime() < fromMillis) {
                continue;
            }
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
            if (isIgnore(subj)) {
                continue;
            }

            cal.setTime(start);
            int date = cal.get(Calendar.DATE);
            int month = cal.get(Calendar.MONTH);
            if (date != prevDate || month != prevMonth) {
                if (date == today && month == todayMonth) {
                    // 日全体の予定があって翌日終了のため●になったのを戻す
                    headmark = "▼";
                } else {
                    headmark = "●";
                }
                if (month != prevMonth) {
                    fmt.format("%s%2$tm月%2$td日", headmark, start);
                } else {
                    fmt.format("%s%td日", headmark, start);
                }
            } else {
                sb.append(headmark);
            }
            prevDate = date;
            prevMonth = month;

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

            // end
            cal.setTime(end);
            date = cal.get(Calendar.DATE);
            month = cal.get(Calendar.MONTH);
            if (date != prevDate) {
                fmt.format("%td日", end);
                if (date == today && month == todayMonth) {
                    headmark = "▼";
                } else {
                    headmark = "●";
                }
            }
            prevDate = date;
            prevMonth = month;
            fmt.format("%tR", end);

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

    boolean isIgnore(String subject) {
        for (String key : ignoreProp.stringPropertyNames()) {
            if (subject.matches(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 会議室名を短くする。
     * 例: "本社)川崎共通 本1 1階 A101会議室 10人"→"A101"
     */
    String shortenLocation(String location) {
        StringBuffer sb = new StringBuffer();
        boolean matched = false;
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
