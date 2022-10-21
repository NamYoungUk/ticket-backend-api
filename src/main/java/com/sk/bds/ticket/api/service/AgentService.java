package com.sk.bds.ticket.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sk.bds.ticket.api.data.model.Agent;
import com.sk.bds.ticket.api.data.model.AgentLevel;
import com.sk.bds.ticket.api.data.model.AppConfig;
import com.sk.bds.ticket.api.exception.AppInternalError;
import com.sk.bds.ticket.api.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

@Slf4j
@Service
public class AgentService {
    AppConfig config;

    @PostConstruct
    public void init() {
        config = AppConfig.getInstance();
        refreshFreshdeskIdMapping();
    }

    public boolean refreshFreshdeskIdMapping() {
        try {
            JSONArray agentArray = FreshdeskService.getAgents();
            List<Agent> freshdeskAgents = Agent.fromFreshdeskArray(agentArray);
            if (config.updateAgentIds(freshdeskAgents)) {
                saveAgentConfig();
                return true;
            }
        } catch (AppInternalError e) {
            log.error("error: {}", e);
        }
        return false;
    }

    public JSONObject listAgents() {
        JSONObject agents = new JSONObject();
        agents.put("l1", listL1Agents());
        agents.put("l2", listL2Agents());
        return agents;
    }

    public JSONArray listL1Agents() {
        List<Agent> agents = config.getL1Agents();
        if (agents != null) {
            try {
                String jsonText = JsonUtil.marshal(agents);
                return new JSONArray(jsonText);
            } catch (JsonProcessingException e) {
                log.error("error:{}", e);
            }
        }
        return new JSONArray();
    }

    public JSONArray listL2Agents() {
        List<Agent> agents = config.getL2Agents();
        if (agents != null) {
            try {
                String jsonText = JsonUtil.marshal(agents);
                return new JSONArray(jsonText);
            } catch (JsonProcessingException e) {
                log.error("error:{}", e);
            }
        }
        return new JSONArray();
    }

    public void addL1Agents(JSONArray agents) {
        List<Agent> l1Agents = Agent.fromArray(agents, AgentLevel.l1);
        addL1Agents(l1Agents);
    }

    public void addL2Agents(JSONArray agents) {
        List<Agent> l2Agents = Agent.fromArray(agents, AgentLevel.l2);
        addL2Agents(l2Agents);
    }

    public void addL1Agents(List<Agent> agents) {
        if (config.addL1Agents(agents)) {
            saveAgentConfig();
            refreshFreshdeskIdMapping();
        }
    }

    public void addL2Agents(List<Agent> agents) {
        if (config.addL2Agents(agents)) {
            saveAgentConfig();
            refreshFreshdeskIdMapping();
        }
    }

    public void removeL1Agents(JSONArray agents) {
        List<Agent> l1Agents = Agent.fromArray(agents, AgentLevel.l1);
        removeL1Agents(l1Agents);
    }

    public void removeL2Agents(JSONArray agents) {
        List<Agent> l2Agents = Agent.fromArray(agents, AgentLevel.l2);
        removeL2Agents(l2Agents);
    }

    public void removeL1Agents(List<Agent> agents) {
        if (config.removeL1Agents(agents)) {
            saveAgentConfig();
        }
    }

    public void removeL2Agents(List<Agent> agents) {
        if (config.removeL2Agents(agents)) {
            saveAgentConfig();
        }
    }

    private void saveAgentConfig() {
        AppConfig.store(config);
    }
}

