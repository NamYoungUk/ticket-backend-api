package com.sk.bds.ticket.api.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.google.gson.*;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Validation;
import java.io.IOException;
import java.util.*;

public class JsonUtil {

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public static ObjectMapper objectMapper() {
        return objectMapper;
    }

    public static void setPropertyNamingStrategy(PropertyNamingStrategy propertyNamingStrategy) {
        objectMapper.setPropertyNamingStrategy(propertyNamingStrategy);
    }

    public static <T> T unmarshal(String jsonText, Class<T> type) throws IOException {
        return objectMapper.readValue(jsonText, type);
    }

    public static String marshal(Object object) throws JsonProcessingException {
        return objectMapper.writeValueAsString(object);
    }

    public static <T> T unmarshalWithValidation(String jsonText, Class<T> type) throws IOException, ConstraintViolationException {
        T t = objectMapper.readValue(jsonText, type);
        Set<ConstraintViolation<Object>> constraintViolations = Validation.buildDefaultValidatorFactory().getValidator().validate(t);
        if (!constraintViolations.isEmpty()) {
            throw new ConstraintViolationException(constraintViolations);
        }
        return t;
    }

    private static Comparator<String> getStringComparator() {
        Comparator<String> c = new Comparator<String>() {
            public int compare(String o1, String o2) {
                return o1.compareTo(o2);
            }
        };
        return c;
    }

    public static void sortJsonElement(JsonElement e) {
        if (e.isJsonNull()) {
            return;
        }

        if (e.isJsonPrimitive()) {
            return;
        }

        if (e.isJsonArray()) {
            JsonArray a = e.getAsJsonArray();
            for (Iterator<JsonElement> it = a.iterator(); it.hasNext(); ) {
                sortJsonElement(it.next());
            }
            return;
        }

        if (e.isJsonObject()) {
            Map<String, JsonElement> tm = new TreeMap<>(getStringComparator());
            for (Map.Entry<String, JsonElement> en : e.getAsJsonObject().entrySet()) {
                tm.put(en.getKey(), en.getValue());
            }

            for (Map.Entry<String, JsonElement> en : tm.entrySet()) {
                e.getAsJsonObject().remove(en.getKey());
                e.getAsJsonObject().add(en.getKey(), en.getValue());
                sortJsonElement(en.getValue());
            }
            return;
        }
    }

    public static String prettyPrint(String jsonText) {
        if (jsonText != null) {
            try {
                JsonElement jsonElement = JsonParser.parseString(jsonText);
                sortJsonElement(jsonElement);
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                return gson.toJson(jsonElement);
            } catch (JsonSyntaxException e) {
                e.printStackTrace();
            }
        }
        return jsonText;
    }

    public static String prettyPrint(JSONObject json) {
        return prettyPrint(json.toString());
    }

    public static String prettyPrint(JSONArray jsonArray) {
        return prettyPrint(jsonArray.toString());
    }
}
