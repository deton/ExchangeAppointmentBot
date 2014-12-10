import java.util.*;
import microsoft.exchange.webservices.data.*;

public class ResponseMessageFormatter {
    Properties prop;
    public ResponseMessageFormatter(Properties prop) {
        this.prop = prop;
    }

    public String format(Collection<CalendarEvent> calendarEvents) {
        if (calendarEvents == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        StringBuilder sb = new StringBuilder();
        Formatter fmt = new Formatter(sb);
        Calendar cal = Calendar.getInstance();
        int prevDate = cal.get(Calendar.DATE);
        String headmark = "▼";
        for (CalendarEvent a : calendarEvents) {
            Date start = a.getStartTime();
            Date end = a.getEndTime();
            // 終了予定後、2時間経過している予定は無視。
            // 終わらず続いている場合は知りたい。
            // TODO: 日付が指定された場合は全て表示
            if (end.getTime() + 2 * 60 * 60 * 1000 < now) {
                continue;
            }
            cal.setTime(start);
            int date = cal.get(Calendar.DATE);
            if (date != prevDate) {
                headmark = "▲";
                fmt.format("%s%td日", headmark, start);
                prevDate = date;
            } else {
                sb.append(headmark);
            }
            fmt.format("%tR", start);
            sb.append("-");
            cal.setTime(end);
            date = cal.get(Calendar.DATE);
            if (date != prevDate) {
                headmark = "▲";
                fmt.format("%s%td日", headmark, end);
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
        }
        return sb.toString();
    }

    String shortenLocation(String location) {
        // TODO
        return location;
    }
}
