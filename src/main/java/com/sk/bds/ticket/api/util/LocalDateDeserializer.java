package com.sk.bds.ticket.api.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.sk.bds.ticket.api.data.model.AppConstants;

import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class LocalDateDeserializer extends JsonDeserializer<Date> {
    private DateFormat formatter;

    public LocalDateDeserializer() {
        formatter = TicketUtil.getLocalDateFormat();
    }

    @Override
    public Date deserialize(JsonParser jsonparser, DeserializationContext context) throws IOException {
        String dateText = jsonparser.getText();
        try {
            return formatter.parse(dateText);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }
}
