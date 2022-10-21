package com.sk.bds.ticket.api.controller;

import com.sk.bds.ticket.api.data.model.RequestBodyParam;
import com.sk.bds.ticket.api.exception.AppError;
import com.sk.bds.ticket.api.response.AppResponse;
import com.sk.bds.ticket.api.service.CloudZService;
import com.sk.bds.ticket.api.service.TicketService;
import com.sk.bds.ticket.api.util.TicketUtil;
import com.sk.bds.ticket.api.util.Util;
import io.swagger.annotations.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@RestController
@RequestMapping(value = "ibm")
public class TicketController {

    @Autowired
    TicketService ticketService;

    @ApiOperation(value = "티켓 생성", notes = "Freshdesk 티켓 ID로 상세 티켓 정보를 조회하여 CSP에 신규 케이스를 등록합니다.\n* Body 정보에 Freshdesk의 티켓 ID를 포함해서 호출하고, CSP에 티켓  등록 후 CSP 케이스 ID를 Freshdesk 해당 티켓의 연관 티켓 ID로 자동 등록합니다.")
    @ApiResponses(value = {
            @ApiResponse(code = 500, message = "INTERNAL_SERVER_ERROR"),
            @ApiResponse(code = 400, message = "BAD_REQUEST"),
            @ApiResponse(code = 401, message = "UNAUTHORIZED"),
            @ApiResponse(code = 404, message = "NOT_FOUND"),
            @ApiResponse(code = 200, message = "OK")
    })
    @ApiImplicitParams({@ApiImplicitParam(name = "authorization", value = "API 인증 토큰 (필수 값)", required = true, dataType = "string", paramType = "header", defaultValue = "token")})
    @PostMapping("createTicket/{freshdeskTicketId}")
    public Callable<AppResponse> createCspTicket(@ApiParam(required = true, value = "freshdeskTicketId: Freshdesk의 티켓 아이디 (필수 값)") @PathVariable("freshdeskTicketId") String freshdeskTicketId, @RequestBody(required = false) String postBody) {
        return () -> {
            if (ticketService.isServiceInitialized()) {
                AppResponse result = ticketService.createCspTicket(freshdeskTicketId, RequestBodyParam.from(postBody));
                return result;
            } else {
                throw new AppError.ServiceUnavailable("Ticket service is not initialized yet.");
            }
        };
    }

    @ApiOperation(value = "티켓 생성", notes = "IBM 티켓 ID로 상세 티켓 정보를 조회하여 Freshdesk에 신규 케이스를 등록합니다.")
    @ApiResponses(value = {
            @ApiResponse(code = 500, message = "INTERNAL_SERVER_ERROR"),
            @ApiResponse(code = 400, message = "BAD_REQUEST"),
            @ApiResponse(code = 401, message = "UNAUTHORIZED"),
            @ApiResponse(code = 404, message = "NOT_FOUND"),
            @ApiResponse(code = 200, message = "OK")
    })
    @ApiImplicitParams({@ApiImplicitParam(name = "authorization", value = "API 인증 토큰 (필수 값)", required = true, dataType = "string", paramType = "header", defaultValue = "token")})
    @PostMapping("createReverseTicket")
    public Callable<AppResponse> createFreshdeskTicket(@RequestBody(required = false) String postBody) {
        return () -> {
            if (ticketService.isServiceInitialized()) {
                if (postBody != null) {
                    JSONArray ticketIdArray = new JSONArray(postBody);
                    if (ticketIdArray.length() > 0) {
                        List<String> ticketIdList = new ArrayList<>();
                        for (int i = 0; i < ticketIdArray.length(); i++) {
                            ticketIdList.add(ticketIdArray.optString(i));
                        }
                        ticketService.createFreshdeskTicket(ticketIdList);
                    }
                }
                return AppResponse.from();
            } else {
                throw new AppError.ServiceUnavailable("Ticket service is not initialized yet.");
            }
        };
    }

    @ApiOperation(value = "티켓 상태 변경", notes = "등록된 CSP 케이스의 상태를 변경합니다.\n* URL 경로에 Freshdesk의 티켓 아이디 포함하여 호출하면, 연관 티켓 id로 CSP의 케이스를 찾아서 티켓 상태 변경을 수행 합니다.")
    @ApiResponses(value = {
            @ApiResponse(code = 500, message = "INTERNAL_SERVER_ERROR"),
            @ApiResponse(code = 400, message = "BAD_REQUEST"),
            @ApiResponse(code = 401, message = "UNAUTHORIZED"),
            @ApiResponse(code = 404, message = "NOT_FOUND"),
            @ApiResponse(code = 409, message = "CONFLICT"),
            @ApiResponse(code = 200, message = "OK")
    })
    @ApiImplicitParams({@ApiImplicitParam(name = "authorization", value = "API 인증 토큰 (필수 값)", required = true, dataType = "string", paramType = "header", defaultValue = "token")})
    @PostMapping("changeTicketStatus/{freshdeskTicketId}")
    public Callable<AppResponse> changeTicketStatus(@ApiParam(required = true, value = "freshdeskTicketId: Freshdesk의 티켓 아이디 (필수 값)") @PathVariable("freshdeskTicketId") String freshdeskTicketId, @RequestBody(required = false) String postBody) {
        return () -> {
            if (ticketService.isServiceInitialized()) {
                AppResponse result = ticketService.changeTicketStatus(freshdeskTicketId, RequestBodyParam.from(postBody));
                return result;
            } else {
                throw new AppError.ServiceUnavailable("Ticket service is not initialized yet.");
            }
        };
    }

    @ApiOperation(value = "티켓 삭제", notes = "티켓의 동기화 작업을 중지 합니다.")
    @ApiResponses(value = {
            @ApiResponse(code = 500, message = "INTERNAL_SERVER_ERROR"),
            @ApiResponse(code = 400, message = "BAD_REQUEST"),
            @ApiResponse(code = 401, message = "UNAUTHORIZED"),
            @ApiResponse(code = 404, message = "NOT_FOUND"),
            @ApiResponse(code = 409, message = "CONFLICT"),
            @ApiResponse(code = 200, message = "OK")
    })
    @ApiImplicitParams({@ApiImplicitParam(name = "authorization", value = "API 인증 토큰 (필수 값)", required = true, dataType = "string", paramType = "header", defaultValue = "token")})
    @PostMapping("deleteTicket/{fdTicketId}")
    public Callable<AppResponse> deleteTicket(@ApiParam(required = true, value = "fdTicketId: Freshdesk의 티켓 아이디 (필수 값)") @PathVariable("fdTicketId") String fdTicketId, @RequestBody(required = false) String postBody) {
        return () -> {
            if (ticketService.isServiceInitialized()) {
                ticketService.removeMonitoringTicket(fdTicketId);
                return AppResponse.from();
            } else {
                throw new AppError.ServiceUnavailable("Ticket service is not initialized yet.");
            }
        };
    }

    @ApiOperation(value = "동기화 목록에 티켓 추가", notes = "Freshdesk의 티켓을 동기화 목록에 추가합니다. Escalation 상태가 Y 이고, Open 상태의 티켓 이어야 합니다.")
    @ApiResponses(value = {
            @ApiResponse(code = 500, message = "INTERNAL_SERVER_ERROR"),
            @ApiResponse(code = 400, message = "BAD_REQUEST"),
            @ApiResponse(code = 401, message = "UNAUTHORIZED"),
            @ApiResponse(code = 404, message = "NOT_FOUND"),
            @ApiResponse(code = 409, message = "CONFLICT"),
            @ApiResponse(code = 200, message = "OK")
    })
    @ApiImplicitParams({@ApiImplicitParam(name = "authorization", value = "API 인증 토큰 (필수 값)", required = true, dataType = "string", paramType = "header", defaultValue = "token")})
    @PostMapping("addMonitoring")
    public Callable<AppResponse> addMonitoring(@RequestBody(required = false) String postBody, @RequestParam(name = "force", required = false, defaultValue = "true") boolean force) {
        return () -> {
            if (ticketService.isServiceInitialized()) {
                if (postBody != null) {
                    JSONArray ticketIdArray = new JSONArray(postBody);
                    if (ticketIdArray.length() > 0) {
                        List<String> ticketIdList = new ArrayList<>();
                        for (int i = 0; i < ticketIdArray.length(); i++) {
                            ticketIdList.add(ticketIdArray.optString(i));
                        }
                        ticketService.addMonitoringTicketList(ticketIdList, force);
                    }
                }
                return AppResponse.from();
            } else {
                throw new AppError.ServiceUnavailable("Ticket service is not initialized yet.");
            }
        };
    }

    @ApiOperation(value = "동기화 목록에서 티켓 제거", notes = "Freshdesk의 티켓을 동기화 목록에서 제거합니다.")
    @ApiResponses(value = {
            @ApiResponse(code = 500, message = "INTERNAL_SERVER_ERROR"),
            @ApiResponse(code = 400, message = "BAD_REQUEST"),
            @ApiResponse(code = 401, message = "UNAUTHORIZED"),
            @ApiResponse(code = 404, message = "NOT_FOUND"),
            @ApiResponse(code = 409, message = "CONFLICT"),
            @ApiResponse(code = 200, message = "OK")
    })
    @ApiImplicitParams({@ApiImplicitParam(name = "authorization", value = "API 인증 토큰 (필수 값)", required = true, dataType = "string", paramType = "header", defaultValue = "token")})
    @PostMapping("removeMonitoring")
    public Callable<AppResponse> removeMonitoring(@RequestBody(required = false) String postBody) {
        return () -> {
            if (ticketService.isServiceInitialized()) {
                if (postBody != null) {
                    JSONArray ticketIdArray = new JSONArray(postBody);
                    if (ticketIdArray.length() > 0) {
                        List<String> ticketIdList = new ArrayList<>();
                        for (int i = 0; i < ticketIdArray.length(); i++) {
                            ticketIdList.add(ticketIdArray.optString(i));
                        }
                        ticketService.removeMonitoringTicketList(ticketIdList);
                    }
                }
                return AppResponse.from();
            } else {
                throw new AppError.ServiceUnavailable("Ticket service is not initialized yet.");
            }
        };
    }

    @ApiOperation(value = "티켓 노트 동기화", notes = "연결된 CSP 케이스에 대화를 동기화 합니다.")
    @ApiResponses(value = {
            @ApiResponse(code = 500, message = "INTERNAL_SERVER_ERROR"),
            @ApiResponse(code = 400, message = "BAD_REQUEST"),
            @ApiResponse(code = 401, message = "UNAUTHORIZED"),
            @ApiResponse(code = 404, message = "NOT_FOUND"),
            @ApiResponse(code = 409, message = "CONFLICT"),
            @ApiResponse(code = 200, message = "OK")
    })
    @ApiImplicitParams({@ApiImplicitParam(name = "authorization", value = "API 인증 토큰 (필수 값)", required = true, dataType = "string", paramType = "header", defaultValue = "token")})
    @PostMapping(path = {"syncConversation/{freshdeskTicketId}", "syncTicketNote/{freshdeskTicketId}"})
    public Callable<Object> syncConversation(@ApiParam(required = true, value = "freshdeskTicketId: Freshdesk의 티켓 아이디 (필수 값)") @PathVariable("freshdeskTicketId") String freshdeskTicketId, @RequestParam(name = "trigger", required = false, defaultValue = "auto") String trigger, @RequestBody(required = false) String postBody) {
        return () -> {
            if (ticketService.isServiceInitialized()) {
                ticketService.synchronizeTicketConversation(freshdeskTicketId, trigger, RequestBodyParam.from(postBody));
                return AppResponse.from();
            } else {
                throw new AppError.ServiceUnavailable("Ticket service is not initialized yet.");
            }
        };
    }

    @ApiOperation(value = "티켓 노트 동기화", notes = "연결된 CSP 케이스에 대화를 동기화 합니다.\n* 요청 body에 Freshdesk의 티켓 아이디 목록을 전달하면, 해당 티켓들의 동기화 작업을 진행합니다.")
    @ApiResponses(value = {
            @ApiResponse(code = 500, message = "INTERNAL_SERVER_ERROR"),
            @ApiResponse(code = 400, message = "BAD_REQUEST"),
            @ApiResponse(code = 401, message = "UNAUTHORIZED"),
            @ApiResponse(code = 404, message = "NOT_FOUND"),
            @ApiResponse(code = 409, message = "CONFLICT"),
            @ApiResponse(code = 200, message = "OK")
    })
    @ApiImplicitParams({@ApiImplicitParam(name = "authorization", value = "API 인증 토큰 (필수 값)", required = true, dataType = "string", paramType = "header", defaultValue = "token")})
    @PostMapping("syncTickets")
    public Callable<Object> syncTicketNote(@RequestBody(required = false) String postBody, @RequestParam(name = "trigger", required = false, defaultValue = "auto") String trigger) {
        return () -> {
            if (ticketService.isServiceInitialized()) {
                if (postBody != null) {
                    JSONArray ticketIdArray = new JSONArray(postBody);
                    if (ticketIdArray.length() > 0) {
                        List<String> ticketIdList = new ArrayList<>();
                        for (int i = 0; i < ticketIdArray.length(); i++) {
                            ticketIdList.add(ticketIdArray.optString(i));
                        }
                        ticketService.synchronizeTicketConversations(ticketIdList, trigger);
                    }
                }
                return AppResponse.from();
            } else {
                throw new AppError.ServiceUnavailable("Ticket service is not initialized yet.");
            }
        };
    }

    @ApiOperation(value = "티켓 Public Url 설정", notes = "티켓의 Public Url 젇보를 설정합니다.")
    @ApiResponses(value = {
            @ApiResponse(code = 500, message = "INTERNAL_SERVER_ERROR"),
            @ApiResponse(code = 400, message = "BAD_REQUEST"),
            @ApiResponse(code = 401, message = "UNAUTHORIZED"),
            @ApiResponse(code = 404, message = "NOT_FOUND"),
            @ApiResponse(code = 409, message = "CONFLICT"),
            @ApiResponse(code = 200, message = "OK")
    })
    @ApiImplicitParams({@ApiImplicitParam(name = "authorization", value = "API 인증 토큰 (필수 값)", required = true, dataType = "string", paramType = "header", defaultValue = "token")})
    @PostMapping("setPublicUrl/{freshdeskTicketId}")
    public Callable<AppResponse> setPublicUrl(@ApiParam(required = true, value = "freshdeskTicketId: Freshdesk의 티켓 아이디 (필수 값)") @PathVariable("freshdeskTicketId") String freshdeskTicketId, @RequestBody(required = false) String postBody) {
        return () -> {
            if (ticketService.isServiceInitialized()) {
                RequestBodyParam bodyParam = RequestBodyParam.from(postBody);
                ticketService.setTicketPublicUrl(freshdeskTicketId, bodyParam.getPublicUrl());
                return AppResponse.from();
            } else {
                throw new AppError.ServiceUnavailable("Ticket service is not initialized yet.");
            }
        };
    }

    /////////////////////////////// Administration API for management.
    @PostMapping("restartService")
    public Callable<Object> restartTicketService() {
        return () -> {
            final int delaySeconds = 10;
            JSONObject result = ticketService.restartService(delaySeconds);
            return result.toString();
        };
    }

    @PostMapping("cancelRestartService")
    public Callable<Object> cancelRestart() {
        return () -> {
            JSONObject result = ticketService.cancelRestartService();
            return result.toString();
        };
    }

    @GetMapping("getStatus")
    public Callable<Object> getAppStatus() {
        return () -> {
            return ticketService.printServiceStatus();
        };
    }

    @GetMapping("getAccountCache")
    public Callable<Object> getAccountCache() {
        return () -> {
            return CloudZService.printAccountCache();
        };
    }

    @GetMapping("getServiceConfig")
    public Callable<Object> getAppConfig(@RequestParam(name = "builtin", required = false, defaultValue = "false") boolean builtin) {
        return () -> {
            return ticketService.getAppConfig(builtin);
        };
    }

    @PostMapping("setServiceConfig")
    public Callable<Object> setAppConfig(@RequestBody(required = false) String configJsonText, @RequestParam(name = "silentChange", required = false, defaultValue = "false") boolean silentChange) {
        return () -> {
            String configureText = ticketService.setAppConfig(configJsonText, silentChange);
            return configureText;
        };
    }

    @PostMapping("startSyncSchedule")
    public Callable<Object> startTicketSyncSchedule() {
        return () -> {
            ticketService.startTicketSyncSchedule(2000);
            return AppResponse.from();
        };
    }

    @PostMapping("stopSyncSchedule")
    public Callable<Object> stopTicketSyncSchedule() {
        return () -> {
            ticketService.stopTicketSyncSchedule();
            return AppResponse.from();
        };
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////
    //IBM Only
    ////////////////////////////////////////////////////////////////////////////////////////////////////////
    @ApiOperation(value = "사용자 계정에 연관된 Device 목록 조회", notes = "사용자의 Email와 Account로 사용중인 Device 목록을 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(code = 500, message = "INTERNAL_SERVER_ERROR"),
            @ApiResponse(code = 400, message = "BAD_REQUEST"),
            @ApiResponse(code = 401, message = "UNAUTHORIZED"),
            @ApiResponse(code = 404, message = "NOT_FOUND"),
            @ApiResponse(code = 409, message = "CONFLICT"),
            @ApiResponse(code = 200, message = "OK")
    })
    @ApiImplicitParams({@ApiImplicitParam(name = "authorization", value = "API 인증 토큰 (필수 값)", required = true, dataType = "String", paramType = "header", defaultValue = "token")})
    @GetMapping("getAccountDevices")
    public Callable<Object> getAccountDevices(@RequestParam(name = "email", required = true, defaultValue = "") String email, @RequestParam(name = "account", required = true, defaultValue = "") String account) {
        return () -> {
            if (ticketService.isServiceInitialized()) {
                try {
                    JSONObject result = TicketUtil.getAccountDevicesByEmail(email, account);
                    return result.toString();
                } catch (AppError e) {
                    Util.ignoreException(e);
                }
                return "[]";
            } else {
                throw new AppError.ServiceUnavailable("Ticket service is not initialized yet.");
            }
        };
    }

    @GetMapping("getCustomerCache")
    public Callable<Object> getCustomerCache() {
        return () -> {
            return CloudZService.printCustomerCache();
        };
    }

}
