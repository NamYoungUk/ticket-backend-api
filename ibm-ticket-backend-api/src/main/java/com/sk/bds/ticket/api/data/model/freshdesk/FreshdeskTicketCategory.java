package com.sk.bds.ticket.api.data.model.freshdesk;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

@Data
@Slf4j
public class FreshdeskTicketCategory {
    private static final String KeyId = "id";
    private static final String KeyPosition = "position";
    private static final String KeyValue = "value";
    private static final String KeyParentChoiceId = "parent_choice_id";
    private static final String KeyChoices = "choices";
    private static final String KeyName = "name";
    private static final String KeyLabel = "label";
    private static final String KeyDeleted = "deleted";


    @JsonProperty(KeyId)
    long id;
    @JsonProperty(KeyPosition)
    int position;
    @JsonProperty(KeyName)
    String name;
    @JsonProperty(KeyLabel)
    String label;
    @JsonProperty(KeyChoices)
    List<ChoiceItem> choices;
    @JsonIgnore
    JSONObject categorySource;

    public FreshdeskTicketCategory() {
    }

    public FreshdeskTicketCategory(FreshdeskTicketCategory another) {
        if (another != null) {
            setId(another.getId());
            setPosition(another.getPosition());
            setName(another.getName());
            setLabel(another.getLabel());
        }
    }

    public boolean hasChoices() {
        return (choices != null) && (choices.size() > 0);
    }

    public int getChoicesCount() {
        if (choices != null) {
            return choices.size();
        }
        return 0;
    }

    public void addChoice(ChoiceItem choiceItem) {
        if (choiceItem != null) {
            if (choices == null) {
                choices = new ArrayList<>();
            }
            if (!choices.contains(choiceItem)) {
                choices.add(choiceItem);
            }
        }
    }

    public JSONObject toCreate() {
        return export();
    }

    public JSONObject toUpdate() {
        return export();
    }

    public JSONObject toDeleteChildren() {
        JSONObject output = new JSONObject();
        if (hasChoices()) {
            JSONArray array = new JSONArray();
            for (ChoiceItem item : choices) {
                array.put(item.toDeleteSelf());
            }
            output.put(KeyChoices, array);
        }
        return output;
    }

    public JSONObject toAddChildren() {
        JSONObject output = new JSONObject();
        if (choices != null && choices.size() > 0) {
            Collections.sort(choices, new Comparator<ChoiceItem>() {
                @Override
                public int compare(ChoiceItem o1, ChoiceItem o2) {
                    return o1.getValue().compareTo(o2.getValue());
                }
            });
            int position = 1;
            JSONArray array = new JSONArray();
            for (ChoiceItem item : choices) {
                item.setPosition(position++);
                array.put(item.toCreate());
            }
            output.put(KeyChoices, array);
        }
        return output;
    }

    public JSONArray getChoicesArray() {
        JSONArray array = new JSONArray();
        if (hasChoices()) {
            for (ChoiceItem item : choices) {
                array.put(item.export());
            }
        }
        return array;
    }

    public JSONObject export() {
        JSONObject output = new JSONObject();
        output.put(KeyId, id);
        output.put(KeyPosition, position);
        output.put(KeyName, name);
        output.put(KeyLabel, label);
        if (choices != null) {
            output.put(KeyChoices, getChoicesArray());
        }
        return output;
    }

    public TreeMap<String, TreeMap<String, List<String>>> getCategoryAsMap() {
        TreeMap<String, TreeMap<String, List<String>>> freshdeskCategories = new TreeMap<>((s1, s2) -> s1.compareTo(s2));
        int l2Count = 0;
        int l3Count = 0;
        JSONObject fdCategory = getCategorySource();
        if (fdCategory != null) {
            JSONArray l1Items = fdCategory.getJSONArray(KeyChoices);
            for (int a = 0; a < l1Items.length(); a++) {
                JSONObject l1Item = l1Items.optJSONObject(a);
                String l1Name = l1Item.optString(KeyValue);
                JSONArray l2Items = l1Item.optJSONArray(KeyChoices);
                TreeMap<String, List<String>> l2Map = new TreeMap<>((s1, s2) -> s1.compareTo(s2));
                l2Count += l2Items.length();
                if (l2Items.length() > 0) {
                    for (int b = 0; b < l2Items.length(); b++) {
                        JSONObject l2Item = l2Items.optJSONObject(b);
                        String l2Name = l2Item.optString(KeyValue);
                        JSONArray l3Items = l2Item.optJSONArray(KeyChoices);
                        List<String> l3Names = new ArrayList<>();
                        l3Count += l3Items.length();
                        if (l3Items.length() > 0) {
                            for (int c = 0; c < l3Items.length(); c++) {
                                JSONObject l3Item = l3Items.optJSONObject(c);
                                String l3Name = l3Item.optString(KeyValue);
                                l3Names.add(l3Name);
                            }
                            l3Names.sort(null);
                        }
                        l2Map.put(l2Name, l3Names);
                    }
                }
                freshdeskCategories.put(l1Name, l2Map);
            }
        } else {
            log.error("Freshdesk category source is empty.");
        }
        log.debug("l2Count: {}, l3Count: {}", l2Count, l3Count);
        return freshdeskCategories;
    }

    @Data
    public static class ChoiceItem {
        @JsonProperty(KeyId)
        long id;
        @JsonProperty(KeyPosition)
        int position;
        @JsonProperty(KeyValue)
        String value;
        @JsonProperty(KeyParentChoiceId)
        long parentChoiceId;
        @JsonProperty(KeyChoices)
        List<ChoiceItem> choices;

        public boolean hasChoices() {
            return (choices != null) && (choices.size() > 0);
        }

        public int getChoicesCount() {
            if (choices != null) {
                return choices.size();
            }
            return 0;
        }

        public void addChoice(ChoiceItem choiceItem) {
            if (choiceItem != null) {
                if (choices == null) {
                    choices = new ArrayList<>();
                }
                if (!choices.contains(choiceItem)) {
                    choices.add(choiceItem);
                }
            }
        }

        public JSONObject toCreate() {
            JSONObject output = new JSONObject();
            output.put(KeyPosition, position);
            output.put(KeyValue, value);
            if (hasChoices()) {
                output.put(KeyChoices, getChoicesArrayForCreate());
            }
            return output;
        }

        public JSONObject toUpdate() {
            JSONObject output = new JSONObject();
            output.put(KeyValue, value);
            output.put(KeyId, id);
            return output;
        }

        public JSONObject toDeleteSelf() {
            JSONObject output = new JSONObject();
            output.put(KeyDeleted, true);
            output.put(KeyId, id);
            return output;
        }

        public JSONArray getChoicesArrayForCreate() {
            int position = 1;
            JSONArray array = new JSONArray();
            if (hasChoices()) {
                for (ChoiceItem item : choices) {
                    item.setPosition(position++);
                    array.put(item.toCreate());
                }
            }
            return array;
        }

        public JSONArray getChoicesArray() {
            JSONArray array = new JSONArray();
            if (hasChoices()) {
                for (ChoiceItem item : choices) {
                    array.put(item.export());
                }
            }
            return array;
        }

        public JSONObject export() {
            JSONObject output = new JSONObject();
            output.put(KeyId, id);
            output.put(KeyPosition, position);
            output.put(KeyValue, value);
            output.put(KeyParentChoiceId, parentChoiceId);
            if (choices != null) {
                output.put(KeyChoices, getChoicesArray());
            }
            return output;
        }
    }
}
