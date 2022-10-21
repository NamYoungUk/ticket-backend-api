package com.sk.bds.ticket.api.controller;

import com.sk.bds.ticket.api.exception.AppError;
import com.sk.bds.ticket.api.response.AppResponse;
import com.sk.bds.ticket.api.service.AgentService;
import com.sk.bds.ticket.api.util.JsonUtil;
import io.swagger.annotations.*;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import springfox.documentation.annotations.ApiIgnore;

import java.util.concurrent.Callable;

@Slf4j
@RestController
@RequestMapping(value = "agent")
public class AgentController {
    @Autowired
    AgentService agentService;

    @ApiOperation(value = "모든 Agent 목록 조회", notes = "등록된 Agent 목록을 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(code = 500, message = "INTERNAL_SERVER_ERROR"),
            @ApiResponse(code = 400, message = "BAD_REQUEST"),
            @ApiResponse(code = 401, message = "UNAUTHORIZED"),
            @ApiResponse(code = 404, message = "NOT_FOUND"),
            @ApiResponse(code = 409, message = "CONFLICT"),
            @ApiResponse(code = 200, message = "OK")
    })
    @ApiImplicitParams({@ApiImplicitParam(name = "authorization", value = "API 인증 토큰 (필수 값)", required = true, dataType = "string", paramType = "header", defaultValue = "token")})
    @GetMapping(path = "list")
    public Callable<Object> agents() {
        return () -> {
            JSONObject agentArray = agentService.listAgents();
            return JsonUtil.prettyPrint(agentArray);
        };
    }

    @ApiOperation(value = "L1 Agent 목록 조회", notes = "등록된 L1 Agent 목록을 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(code = 500, message = "INTERNAL_SERVER_ERROR"),
            @ApiResponse(code = 400, message = "BAD_REQUEST"),
            @ApiResponse(code = 401, message = "UNAUTHORIZED"),
            @ApiResponse(code = 404, message = "NOT_FOUND"),
            @ApiResponse(code = 409, message = "CONFLICT"),
            @ApiResponse(code = 200, message = "OK")
    })
    @ApiImplicitParams({@ApiImplicitParam(name = "authorization", value = "API 인증 토큰 (필수 값)", required = true, dataType = "string", paramType = "header", defaultValue = "token")})
    @GetMapping(path = "list/l1")
    public Callable<Object> l1AgentList() {
        return () -> {
            JSONArray agentArray = agentService.listL1Agents();
            return JsonUtil.prettyPrint(agentArray);
        };
    }

    @ApiOperation(value = "L2 Agent 목록 조회", notes = "등록된 L2 Agent 목록을 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(code = 500, message = "INTERNAL_SERVER_ERROR"),
            @ApiResponse(code = 400, message = "BAD_REQUEST"),
            @ApiResponse(code = 401, message = "UNAUTHORIZED"),
            @ApiResponse(code = 404, message = "NOT_FOUND"),
            @ApiResponse(code = 409, message = "CONFLICT"),
            @ApiResponse(code = 200, message = "OK")
    })
    @ApiImplicitParams({@ApiImplicitParam(name = "authorization", value = "API 인증 토큰 (필수 값)", required = true, dataType = "string", paramType = "header", defaultValue = "token")})
    @GetMapping(path = "list/l2")
    public Callable<Object> l2AgentList() {
        return () -> {
            JSONArray agentArray = agentService.listL2Agents();
            return JsonUtil.prettyPrint(agentArray);
        };
    }

    @ApiOperation(value = "L1 Agent 추가", notes = "L1 Agent를 추가합니다.")
    @ApiResponses(value = {
            @ApiResponse(code = 500, message = "INTERNAL_SERVER_ERROR"),
            @ApiResponse(code = 400, message = "BAD_REQUEST"),
            @ApiResponse(code = 401, message = "UNAUTHORIZED"),
            @ApiResponse(code = 404, message = "NOT_FOUND"),
            @ApiResponse(code = 409, message = "CONFLICT"),
            @ApiResponse(code = 200, message = "OK")
    })
    @ApiImplicitParams({@ApiImplicitParam(name = "authorization", value = "API 인증 토큰 (필수 값)", required = true, dataType = "string", paramType = "header", defaultValue = "token"),
            @ApiImplicitParam(name = "agents", value = "추가할 Agent 목록(필수 값). [{\"name\": \"string\", \"email\": \"string\"}]", dataType = "string", paramType = "body", defaultValue = "[]", required = true)})
    @PostMapping("add/l1")
    public Callable<Object> addL1(@ApiIgnore @RequestBody String agents) {
        return () -> {
            log.debug("agents:{}", agents);
            try {
                JSONArray agentArray = new JSONArray(agents);
                agentService.addL1Agents(agentArray);
                JSONArray l1Agents = agentService.listL1Agents();
                return JsonUtil.prettyPrint(l1Agents);
                //return AppResponse.from();
            } catch (JSONException e) {
                throw new AppError.BadRequest("Invalid JSON syntax. " + e.getMessage());
            }
        };
    }

    @ApiOperation(value = "L2 Agent 추가", notes = "L2 Agent를 추가합니다.")
    @ApiResponses(value = {
            @ApiResponse(code = 500, message = "INTERNAL_SERVER_ERROR"),
            @ApiResponse(code = 400, message = "BAD_REQUEST"),
            @ApiResponse(code = 401, message = "UNAUTHORIZED"),
            @ApiResponse(code = 404, message = "NOT_FOUND"),
            @ApiResponse(code = 409, message = "CONFLICT"),
            @ApiResponse(code = 200, message = "OK")
    })
    @ApiImplicitParams({@ApiImplicitParam(name = "authorization", value = "API 인증 토큰 (필수 값)", required = true, dataType = "string", paramType = "header", defaultValue = "token"),
            @ApiImplicitParam(name = "agents", value = "추가할 Agent 목록(필수 값). [{\"name\": \"string\", \"email\": \"string\"}]", dataType = "string", paramType = "body", defaultValue = "[]", required = true)})
    @PostMapping("add/l2")
    public Callable<Object> addL2(@ApiIgnore @RequestBody String agents) {
        return () -> {
            log.debug("agentArray:{}", agents);
            try {
                JSONArray agentArray = new JSONArray(agents);
                agentService.addL2Agents(agentArray);
                JSONArray l2Agents = agentService.listL2Agents();
                return JsonUtil.prettyPrint(l2Agents);
                //return AppResponse.from();
            } catch (JSONException e) {
                throw new AppError.BadRequest("Invalid JSON syntax. " + e.getMessage());
            }
        };
    }

    @ApiOperation(value = "L1 Agent 삭제", notes = "L1 Agent를 삭제합니다.")
    @ApiResponses(value = {
            @ApiResponse(code = 500, message = "INTERNAL_SERVER_ERROR"),
            @ApiResponse(code = 400, message = "BAD_REQUEST"),
            @ApiResponse(code = 401, message = "UNAUTHORIZED"),
            @ApiResponse(code = 404, message = "NOT_FOUND"),
            @ApiResponse(code = 409, message = "CONFLICT"),
            @ApiResponse(code = 200, message = "OK")
    })
    @ApiImplicitParams({@ApiImplicitParam(name = "authorization", value = "API 인증 토큰 (필수 값)", required = true, dataType = "string", paramType = "header", defaultValue = "token"),
            @ApiImplicitParam(name = "agents", value = "삭제할 Agent 목록(필수 값). [{\"name\": \"string\", \"email\": \"string\"}]", dataType = "string", paramType = "body", defaultValue = "[]", required = true)})
    @PostMapping("remove/l1")
    public Callable<Object> removeL1(@ApiIgnore @RequestBody String agents) {
        return () -> {
            log.debug("agentArray:{}", agents);
            try {
                JSONArray agentArray = new JSONArray(agents);
                agentService.removeL1Agents(agentArray);
                JSONArray l1Agents = agentService.listL1Agents();
                return JsonUtil.prettyPrint(l1Agents);
                //return AppResponse.from();
            } catch (JSONException e) {
                throw new AppError.BadRequest("Invalid JSON syntax. " + e.getMessage());
            }
        };
    }

    @ApiOperation(value = "L2 Agent 삭제", notes = "L2 Agent를 삭제합니다.")
    @ApiResponses(value = {
            @ApiResponse(code = 500, message = "INTERNAL_SERVER_ERROR"),
            @ApiResponse(code = 400, message = "BAD_REQUEST"),
            @ApiResponse(code = 401, message = "UNAUTHORIZED"),
            @ApiResponse(code = 404, message = "NOT_FOUND"),
            @ApiResponse(code = 409, message = "CONFLICT"),
            @ApiResponse(code = 200, message = "OK")
    })
    @ApiImplicitParams({@ApiImplicitParam(name = "authorization", value = "API 인증 토큰 (필수 값)", required = true, dataType = "string", paramType = "header", defaultValue = "token"),
            @ApiImplicitParam(name = "agents", value = "삭제할 Agent 목록(필수 값). [{\"name\": \"string\", \"email\": \"string\"}]", dataType = "string", paramType = "body", defaultValue = "[]", required = true)})
    @PostMapping("remove/l2")
    public Callable<Object> removeL2(@ApiIgnore @RequestBody String agents) {
        return () -> {
            log.debug("agentArray:{}", agents);
            try {
                JSONArray agentArray = new JSONArray(agents);
                agentService.removeL2Agents(agentArray);
                JSONArray l2Agents = agentService.listL2Agents();
                return JsonUtil.prettyPrint(l2Agents);
                //return AppResponse.from();
            } catch (JSONException e) {
                throw new AppError.BadRequest("Invalid JSON syntax. " + e.getMessage());
            }
        };
    }

    @ApiOperation(value = "Freshdesk의 User Id/Email과 L1,L2 파트 매핑", notes = "Freshdesk Id/Email과 L1,L2 파트를 매핑합니다.")
    @ApiResponses(value = {
            @ApiResponse(code = 500, message = "INTERNAL_SERVER_ERROR"),
            @ApiResponse(code = 400, message = "BAD_REQUEST"),
            @ApiResponse(code = 401, message = "UNAUTHORIZED"),
            @ApiResponse(code = 404, message = "NOT_FOUND"),
            @ApiResponse(code = 409, message = "CONFLICT"),
            @ApiResponse(code = 200, message = "OK")
    })
    @ApiImplicitParams({@ApiImplicitParam(name = "authorization", value = "API 인증 토큰 (필수 값)", required = true, dataType = "string", paramType = "header", defaultValue = "token")})
    @PostMapping("refresh/mapping")
    public Callable<Object> refreshFreshdeskIdMapping() {
        return () -> {
            log.debug("");
            agentService.refreshFreshdeskIdMapping();
            return AppResponse.from();
        };
    }

}
