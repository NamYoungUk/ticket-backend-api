package com.sk.bds.ticket.api.data.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sk.bds.ticket.api.util.JsonUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Data
public class Agent {
    private static final String KeyId = "id";
    private static final String KeyName = "name";
    private static final String KeyEmail = "email";
    private static final String KeyContact = "contact";
    private static final String KeyGroupIds = "group_ids";
    private static final String KeyRoleIds = "role_ids";
    private static final String KeyLevel = "level";

    @JsonProperty(KeyId)
    long id;
    @JsonProperty(KeyName)
    String name;
    @JsonProperty(KeyEmail)
    String email;
    @JsonProperty(KeyLevel)
    AgentLevel level;

    public Agent() {
        this.id = 0;
        this.level = AgentLevel.notAssigned;
    }

    public Agent(String name, String email) {
        this.id = 0;
        this.name = name;
        this.email = email;
        level = AgentLevel.notAssigned;
    }

    public Agent(long id, String name, String email) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.level = AgentLevel.notAssigned;
    }

    public Agent(long id, String name, String email, AgentLevel level) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.level = level;
    }

    public boolean equalsId(long id) {
        return this.id == id;
    }

    public boolean equalsEmail(String email) {
        if (email != null) {
            return email.equals(this.email);
        }
        return false;
    }

    public static Agent from(JSONObject info, AgentLevel level) {
        if (info != null) {
            try {
                Agent agent = JsonUtil.unmarshal(info.toString(), Agent.class);
                log.debug("agent:{}", agent);
                agent.setLevel(level);
                return agent;
            } catch (IOException e) {
                log.error("error:{}", e);
            }
        }
        return null;
    }

    public static List<Agent> fromArray(JSONArray agentArray, AgentLevel level) {
        log.debug("agentArray:{}", agentArray);
        List<Agent> agents = new ArrayList<>();
        if (agentArray != null && agentArray.length() > 0) {
            for (int i = 0; i < agentArray.length(); i++) {
                Agent agent = from(agentArray.getJSONObject(i), level);
                if (agent != null) {
                    agents.add(agent);
                }
            }
        }
        return agents;
    }

    public static Agent fromFreshdesk(JSONObject info) {
        if (info != null) {
            if (info.has(KeyId) && info.has(KeyContact)) {
                JSONObject contact = info.getJSONObject(KeyContact);
                if (contact.has(KeyName) && contact.has(KeyEmail)) {
                    return new Agent(info.optLong(KeyId), contact.optString(KeyName), contact.optString(KeyEmail));
                }
            }
        }
        return null;
    }

    public static List<Agent> fromFreshdeskArray(JSONArray agentArray) {
        log.debug("agentArray:{}", agentArray);
        List<Agent> agents = new ArrayList<>();
        if (agentArray != null && agentArray.length() > 0) {
            for (int i = 0; i < agentArray.length(); i++) {
                Agent agent = fromFreshdesk(agentArray.getJSONObject(i));
                if (agent != null) {
                    agents.add(agent);
                }
            }
        }
        return agents;
    }
}
