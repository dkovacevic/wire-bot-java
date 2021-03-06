package com.wire.bots.cali;

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventReminder;
import com.google.api.services.calendar.model.Events;
import com.wire.bots.sdk.ClientRepo;
import com.wire.bots.sdk.WireClient;
import com.wire.bots.sdk.tools.Logger;

import java.io.IOException;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

class AlertManager {
    private static final int PERIOD = 1;
    private final DateFormat dateFormat = new SimpleDateFormat("EEEEE, dd MMMMM 'at' HH:mm");
    private final Timer timer = new Timer();
    private final HashMap<String, Event> remindersMap = new HashMap<>();
    private final Database database;

    AlertManager(Config.DB postgres) {
        this.database = new Database(postgres);
    }

    boolean insertNewSubscriber(String botId) throws Exception {
        return database.insertSubscriber(botId);
    }

    void crone(final ClientRepo repo) {
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    ArrayList<String> subscribers = database.getSubscribers();
                    for (String botId : subscribers) {
                        try (WireClient wireClient = repo.getClient(botId)) {
                            if (wireClient == null) {
                                database.unsubscribe(botId);
                                continue;
                            }
                            fetchEvents(wireClient);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Logger.warning("crone: error: %s", e);
                }
            }
        }, TimeUnit.MINUTES.toMillis(1), TimeUnit.MINUTES.toMillis(PERIOD));
    }

    private void fetchEvents(final WireClient wireClient) {
        try {
            String botId = wireClient.getId();
            Events events = CalendarAPI.listEvents(botId, 1);

            for (final Event event : events.getItems()) {
                try {
                    int i = 0;
                    List<EventReminder> overrides = event.getReminders().getOverrides();
                    if (overrides != null) {
                        for (EventReminder reminder : overrides) {
                            scheduleReminder(wireClient, event, reminder, i++);
                        }
                    } else {
                        for (EventReminder reminder : events.getDefaultReminders()) {
                            scheduleReminder(wireClient, event, reminder, i++);
                        }
                    }
                } catch (Exception e) {
                    Logger.warning("AlertManager.fetchEvents: %s %s %s", botId, event.getId(), e);
                }
            }
        } catch (IOException e) {
            // Logger.warning("AlertManager.fetchEvents: %s", e);
        }
    }

    private void scheduleReminder(WireClient wireClient, Event event, EventReminder reminder, int i) {
        String id = String.format("%s-%s-%d", wireClient.getId(), event.getId(), i);
        if (remindersMap.put(id, event) != null)
            return;

        final DateTime start = event.getStart().getDateTime();
        if (start != null) {
            Date at = new Date(start.getValue() - TimeUnit.MINUTES.toMillis(reminder.getMinutes()));
            if (at.getTime() > System.currentTimeMillis()) {
                scheduleReminder(wireClient, at, event.getId());
            }
        }
    }

    private void scheduleReminder(final WireClient wireClient, final Date at, final String eventId) {
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                String botId = wireClient.getId();
                try {
                    Event event = CalendarAPI.getEvent(botId, eventId);
                    if (event != null) {
                        boolean muted = database.isMuted(botId);
                        if (muted) {
                            Logger.info("scheduleReminder: %s Event: %s Muted", botId, event.getId());
                            return;
                        }

                        if (Objects.equals("cancelled", event.getStatus())) {
                            Logger.info("scheduleReminder: %s Event: %s Cancelled: %s", botId, event.getId(), event.getStatus());
                            return;
                        }

                        int timeZoneShift = event.getStart().getDateTime().getTimeZoneShift();
                        long start = event.getStart().getDateTime().getValue();
                        int minutes = Math.round((start - System.currentTimeMillis()) / 60000f);

                        String msg = String.format("Starting in %d minutes\n[%s](%s)\n%s",
                                minutes,
                                event.getSummary(),
                                event.getHtmlLink(),
                                dateFormat.format(new Date(start + TimeUnit.MINUTES.toMillis(timeZoneShift))));

                        wireClient.ping();
                        wireClient.sendText(msg);
                    }
                } catch (Exception e) {
                    Logger.warning("scheduleReminder: %s error: %s", botId, e);
                }
            }
        }, at);
    }

    boolean removeSubscriber(String botId) throws SQLException {
        return database.unsubscribe(botId);
    }
}
