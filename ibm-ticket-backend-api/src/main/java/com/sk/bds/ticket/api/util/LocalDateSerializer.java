package com.sk.bds.ticket.api.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.sk.bds.ticket.api.data.model.AppConstants;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class LocalDateSerializer extends JsonSerializer<Date> {
    DateFormat formatter;

    public LocalDateSerializer() {
        formatter = TicketUtil.getLocalDateFormat();
    }

    @Override
    public void serialize(Date date, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        jsonGenerator.writeString(formatter.format(date));
    }
}