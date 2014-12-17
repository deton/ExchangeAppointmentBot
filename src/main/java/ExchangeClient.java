// http://stackoverflow.com/questions/15841767/how-to-authenticate-ews-java-api
// http://ameblo.jp/softwaredeveloper/entry-11603208423.html
import java.net.*;
import java.util.*;
import java.util.logging.*;
import microsoft.exchange.webservices.data.*;

public class ExchangeClient {
    static Logger logger = Logger.getLogger("ExchangeClient");

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.out.println("Usage: ExchangeClient <server> <email> <password> <targetemail>");
            System.out.println("   ex: ExchagneClient exchange.example.com taro@example.com p@sSw0rD room-00309@example.com");
            return;
        }
        ExchangeClient ec = new ExchangeClient();
        //ec.outputAppointments(args[0], args[1], args[2], args[3]);
        ec.outputCalendarEvents(args[0], args[1], args[2], args[3]);
    }

    public void outputCalendarEvents(String server, String userId, String password, String email) throws Exception {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        Date startDate = cal.getTime();
        cal.add(Calendar.DATE, 1);
        Date endDate = cal.getTime();
        Collection<CalendarEvent> calendarEvents = getCalendarEvents(server, userId, password, email, startDate, endDate);
        for (CalendarEvent a : calendarEvents) {
            System.out.println("Start: " + a.getStartTime());
            System.out.println("End: " + a.getEndTime());
            String subj = "-";
            String loc = "-";
            CalendarEventDetails details = a.getDetails();
            if (details != null) {
                subj = details.getSubject();
                loc = details.getLocation();
            }
            System.out.println("Subject: " + subj);
            System.out.println("Location: " + loc);
        }
    }

    public void outputAppointments(String server, String userId, String password, String roomAddress) throws Exception {
        FindItemsResults<Appointment> findResults = getAppointments(server, userId, password, roomAddress);
        for (Appointment a : findResults.getItems()) {
            System.out.println("Start: " + a.getStart());
            System.out.println("End: " + a.getEnd());
            System.out.println("Subject: " + a.getSubject());
            System.out.println("Location: " + a.getLocation());
        }
    }

    public FindItemsResults<Appointment> getAppointments(String server, String userId, String password, String roomAddress) throws Exception {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        Date startDate = cal.getTime();
        cal.add(Calendar.DATE, 1);
        Date endDate = cal.getTime();
        /*
        long now = System.currentTimeMillis();
        final long dayInMillis = 24 * 60 * 60 * 1000;
        Date startDate = new Date(now - dayInMillis);
        Date endDate = new Date(now + dayInMillis);
        */
        return getAppointments(server, userId, password, roomAddress, startDate, endDate);
    }

    public FindItemsResults<Appointment> getAppointments(String server, String userId, String password, String roomAddress, Date startDate, Date endDate) throws Exception {
        ExchangeService exchangeService = createExchangeService(server, userId, password);

        // http://blog.liris.org/2011/01/ms-exchange.html
        Mailbox room = new Mailbox(roomAddress);
        FolderId fid = new FolderId(WellKnownFolderName.Calendar, room);
        CalendarFolder cf = CalendarFolder.bind(exchangeService, fid);
        FindItemsResults<Appointment> findResults = cf.findAppointments(new CalendarView(startDate, endDate));
        return findResults;
    }

    ExchangeService createExchangeService(String server, String userId, String password) throws Exception {
        String serverUrl = "https://" + server + "/EWS/Exchange.asmx";
        ExchangeCredentials credentials = new WebCredentials(userId, password);
        ExchangeVersion exchangeVersion = ExchangeVersion.Exchange2010_SP2;
        ExchangeService exchangeService = new ExchangeService(exchangeVersion);
         
        exchangeService.setUrl(new URI(serverUrl));
        exchangeService.setCredentials(credentials);
        return exchangeService;
    }

    public Collection<CalendarEvent> getCalendarEvents(String server, String userId, String password, String email, Date startDate, Date endDate) throws Exception {
        try {
            ExchangeService service = createExchangeService(server, userId, password);
            List<AttendeeInfo> attendees = new ArrayList<AttendeeInfo>();
            attendees.add(new AttendeeInfo(email));

            GetUserAvailabilityResults results = service.getUserAvailability(
                attendees, new TimeWindow(startDate, endDate),
                AvailabilityData.FreeBusy);

            for (AttendeeAvailability attendeeAvailability : results.getAttendeesAvailability()) {
                if (attendeeAvailability.getErrorCode() == ServiceError.NoError) {
                    return attendeeAvailability.getCalendarEvents();
                }
            }
        } catch (Exception ex) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING, "getCalendarEvents", ex);
            }
        }
        return null;
    }
}
