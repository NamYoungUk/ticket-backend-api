package com.sk.bds.ticket.api.data.model;

import lombok.extern.slf4j.Slf4j;

import java.util.Calendar;
import java.util.TimeZone;

@Slf4j
public class TimeSectionGroup {
    public enum SectionInterval {
        minute10,
        minute20,
        minute30,
        hour1,
        hour2,
        hour3,
        hour4,
        hour6,
        hour12,
        date1
    }

    SectionInterval interval;
    long startTime;
    long endTime;
    int sectionOffset;
    TimeZone timeZone;
    boolean reachedEndTime;
    Calendar calendar;

    public TimeSectionGroup(long start, TimeZone timeZone) {
        this(start, timeZone, SectionInterval.date1);
    }

    public TimeSectionGroup(long start, TimeZone timeZone, SectionInterval interval) {
        this.interval = interval;
        this.startTime = start;
        this.endTime = 0;
        this.timeZone = timeZone;
        if (this.timeZone == null) {
            this.timeZone = AppConstants.getUTCTimeZone();
        }
        initialize();
    }

    public TimeSectionGroup(long start, long end, TimeZone timeZone) {
        this(start, end, timeZone, SectionInterval.date1);
    }

    public TimeSectionGroup(long start, long end, TimeZone timeZone, SectionInterval interval) {
        this.interval = interval;
        this.startTime = start;
        this.endTime = end;
        this.timeZone = timeZone;
        if (this.timeZone == null) {
            this.timeZone = AppConstants.getUTCTimeZone();
        }
        initialize();
    }

    private synchronized void initialize() {
        calendar = Calendar.getInstance();
        calendar.setTimeZone(timeZone);
        calendar.setTimeInMillis(startTime);
        sectionOffset = 0;
        reachedEndTime = false;
        log.debug("timezone:{}, timezoneOffset:{}", getTimeZone().getDisplayName(), getTimeZoneOffset());
    }

    public TimeZone getTimeZone() {
        return timeZone;
    }

    public long getTimeZoneOffset() {
        return timeZone.getRawOffset();
    }

    public long getStartTime() {
        return startTime;
    }

    public boolean hasEndTime() {
        return (endTime > startTime);
    }

    public long getEndTime() {
        if (hasEndTime()) {
            return endTime;
        }
        return System.currentTimeMillis();
    }

    public synchronized boolean hasNext() {
        return !reachedEndTime;
    }

    public synchronized TimeSection next() {
        long maxTime = getEndTime();
        long sectionStart;
        long sectionEnd;
        TimeSection section;
        log.debug("maxTime: {}", maxTime);
        sectionStart = calendar.getTimeInMillis();
        nextSection(calendar);
        sectionEnd = calendar.getTimeInMillis() - 1;
        if (sectionEnd >= maxTime) {
            sectionEnd = maxTime;
            reachedEndTime = true;
        }

        if (reachedEndTime && !hasEndTime()) {
            //Final time section but unlimited.
            section = new TimeSection(sectionStart);
        } else {
            section = new TimeSection(sectionStart, sectionEnd);
        }
        sectionOffset++;
        return section;
    }

    private static final long MINUTE = (60 * 1000);
    private static final long HOUR = (60 * MINUTE);
    private static final long DATE = (24 * HOUR);

    private void nextSection(Calendar cal) {
        if (cal != null) {
            long current = cal.getTimeInMillis();
            long start;
            switch (interval) {
                case minute10:
                    start = current - (current % (10 * MINUTE));
                    cal.setTimeInMillis(start);
                    cal.add(Calendar.MINUTE, 10);
                    break;
                case minute20:
                    start = current - (current % (20 * MINUTE));
                    cal.setTimeInMillis(start);
                    cal.add(Calendar.MINUTE, 20);
                    break;
                case minute30:
                    start = current - (current % (30 * MINUTE));
                    cal.setTimeInMillis(start);
                    cal.add(Calendar.MINUTE, 30);
                    break;
                case hour1:
                    start = current - (current % HOUR);
                    cal.setTimeInMillis(start);
                    cal.add(Calendar.HOUR, 1);
                    break;
                case hour2:
                    start = current - (current % (2 * HOUR));
                    cal.setTimeInMillis(start);
                    cal.add(Calendar.HOUR, 2);
                    break;
                case hour3:
                    start = current - (current % (3 * HOUR));
                    cal.setTimeInMillis(start);
                    cal.add(Calendar.HOUR, 3);
                    break;
                case hour4:
                    start = current - (current % (4 * HOUR));
                    cal.setTimeInMillis(start);
                    cal.add(Calendar.HOUR, 4);
                    break;
                case hour6:
                    start = current - (current % (6 * HOUR));
                    cal.setTimeInMillis(start);
                    cal.add(Calendar.HOUR, 6);
                    break;
                case hour12:
                    start = current - (current % (12 * HOUR));
                    cal.setTimeInMillis(start);
                    cal.add(Calendar.HOUR, 12);
                    break;
                case date1:
                    resetTimeToZero(cal);
                    cal.add(Calendar.DATE, 1);
                    break;
            }
        }
    }

    private void resetTimeToZero(Calendar cal) {
        if (cal != null) {
            //Reset Time to 00:00:00 000
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
        }
    }

    private void resetTimeToMax(Calendar cal) {
        if (cal != null) {
            //Reset Time to 23:59:59 999
            cal.set(Calendar.HOUR_OF_DAY, 23);
            cal.set(Calendar.MINUTE, 59);
            cal.set(Calendar.SECOND, 59);
            cal.set(Calendar.MILLISECOND, 999);
        }
    }
}
