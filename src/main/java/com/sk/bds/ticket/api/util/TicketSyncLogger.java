package com.sk.bds.ticket.api.util;

import com.sk.bds.ticket.api.data.model.AppConfig;
import com.sk.bds.ticket.api.data.model.TicketTimeRecord;
import com.sk.bds.ticket.api.util.Util;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class TicketSyncLogger {
    private static AppConfig config = AppConfig.getInstance();

    public static void setTicketSyncTime(long timeMillis) {
        log.info("timeMillis: {}", timeMillis);
        try {
            Util.writeFile(config.getTicketSyncRecordFile(), String.valueOf(timeMillis), false);
        } catch (IOException e) {
            log.error("Failed to write sync time ({}). {}", timeMillis, e);
        }
    }

    public static long getTicketSyncTime() {
        long ticketSyncTime = 0;
        try {
            String timeString = Util.readFile(config.getTicketSyncRecordFile());
            ticketSyncTime = Long.parseLong(timeString);
        } catch (IOException | NumberFormatException e) {
            log.error("Failed to read sync time ({}).");
        }
        if (ticketSyncTime == 0) {
            ticketSyncTime = config.getTicketSyncTargetTime();
            setTicketSyncTime(ticketSyncTime);
        }
        return ticketSyncTime;
    }

    public static void clearTicketSyncTime() {
        log.info("clear ticket sync time");
        try {
            JSONObject empty = new JSONObject();
            Util.writeFile(config.getTicketSyncRecordFile(), empty.toString());
        } catch (JSONException | IOException e) {
            Util.deleteFile(config.getTicketSyncRecordFile());
            log.error("{}", e);
        }
    }

    public static void setReverseSyncLatestTicketTimes(Map<String, TicketTimeRecord> brandTicketTimeRecords) {
        JSONObject storedRecordJson;
        if (brandTicketTimeRecords != null && brandTicketTimeRecords.size() > 0) {
            try {
                String recordText = Util.readFile(config.getTicketReverseSyncRecordFile());
                log.info("saved reverse sync record: {}", recordText);
                storedRecordJson = new JSONObject(recordText);
            } catch (IOException e) {
                log.error("{}", e);
                storedRecordJson = new JSONObject();
            }
            for (String brandId : brandTicketTimeRecords.keySet()) {
                log.info("brandId: {}, timeMillis: {}", brandId, brandTicketTimeRecords.get(brandId));
                try {
                    JSONObject recordJson = brandTicketTimeRecords.get(brandId).export();
                    storedRecordJson.put(brandId, recordJson);
                } catch (JSONException e) {
                    log.error("error : {}", e);
                }
            }
            try {
                Util.writeFile(config.getTicketReverseSyncRecordFile(), storedRecordJson.toString());
            } catch (JSONException | IOException e) {
                log.error("{}", e);
            }
        }
    }

    public static void setReverseSyncLatestTicketTime(String brandId, TicketTimeRecord timeRecord) {
        JSONObject storedRecordJson;
        log.info("brandId: {}, timeRecord: {}", brandId, timeRecord);
        try {
            String recordText = Util.readFile(config.getTicketReverseSyncRecordFile());
            log.info("saved reverse sync record: {}", recordText);
            storedRecordJson = new JSONObject(recordText);
        } catch (IOException e) {
            log.error("{}", e);
            storedRecordJson = new JSONObject();
        }
        try {
            storedRecordJson.put(brandId, timeRecord.export());
            Util.writeFile(config.getTicketReverseSyncRecordFile(), storedRecordJson.toString());
        } catch (JSONException | IOException e) {
            log.error("{}", e);
        }
    }

    public static Map<String, TicketTimeRecord> getReverseSyncLastTicketTimeRecords() {
        Map<String, TicketTimeRecord> records = new ConcurrentHashMap<>();
        try {
            String recordText = Util.readFile(config.getTicketReverseSyncRecordFile());
            log.debug("recordText: {}", recordText);
            JSONObject recordJson = new JSONObject(recordText);
            for (String brandId : recordJson.keySet()) {
                if (recordJson.has(brandId)) {
                    JSONObject brandRecordJson = recordJson.getJSONObject(brandId);
                    TicketTimeRecord timeRecord = TicketTimeRecord.from(brandRecordJson.toString());
                    if (timeRecord != null) {
                        records.put(brandId, timeRecord);
                    }
                }
            }
        } catch (Exception e) {
            log.error("{}", e);
        }
        return records;
    }

    public static TicketTimeRecord getReverseSyncLastTicketTimeRecord(String brandId) {
        try {
            String recordText = Util.readFile(config.getTicketReverseSyncRecordFile());
            log.debug("recordText: {}", recordText);
            JSONObject recordJson = new JSONObject(recordText).getJSONObject(brandId);
            return TicketTimeRecord.from(recordJson.toString());
        } catch (Exception e) {
            log.error("{}", e);
        }
        return new TicketTimeRecord("0", config.getTicketSyncTargetTime());
    }

    public static void clearReverseSyncLastTicketTime() {
        log.info("clear ticket reverse sync time");
        try {
            JSONObject empty = new JSONObject();
            Util.writeFile(config.getTicketReverseSyncRecordFile(), empty.toString());
        } catch (JSONException | IOException e) {
            Util.deleteFile(config.getTicketReverseSyncRecordFile());
            log.error("{}", e);
        }
    }

    public static JSONObject exportReverseSyncRecord() {
        try {
            String recordText = Util.readFile(config.getTicketReverseSyncRecordFile());
            log.debug("recordText: {}", recordText);
            return new JSONObject(recordText);
        } catch (IOException e) {
            log.error("{}", e);
        }
        return new JSONObject();
    }
}
