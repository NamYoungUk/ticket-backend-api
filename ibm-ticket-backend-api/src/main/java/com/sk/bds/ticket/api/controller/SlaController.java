package com.sk.bds.ticket.api.controller;

import com.sk.bds.ticket.api.data.model.SlaReport;
import com.sk.bds.ticket.api.data.model.SlaRequestParam;
import com.sk.bds.ticket.api.data.model.TicketStatus;
import com.sk.bds.ticket.api.exception.AppError;
import com.sk.bds.ticket.api.service.SlaService;
import com.sk.bds.ticket.api.util.JsonUtil;
import com.sk.bds.ticket.api.util.Util;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.Callable;

@Slf4j
@RestController
@RequestMapping(value = "sla")
public class SlaController {
    @Autowired
    SlaService slaService;

    @ApiOperation(value = "Escalation 시간 업데이트", notes = "CSP에 Escalation 된 시간을 티켓에 기록합니다.")
    @ApiResponses(value = {
            @ApiResponse(code = 500, message = "INTERNAL_SERVER_ERROR"),
            @ApiResponse(code = 400, message = "BAD_REQUEST"),
            @ApiResponse(code = 401, message = "UNAUTHORIZED"),
            @ApiResponse(code = 404, message = "NOT_FOUND"),
            @ApiResponse(code = 409, message = "CONFLICT"),
            @ApiResponse(code = 200, message = "OK")
    })
    @PostMapping(path = "escalate/{fdTicketId}")
    public Callable<Object> escalate(@ApiParam(required = true, value = "fdTicketId: Freshdesk의 티켓 아이디 (필수 값)") @PathVariable("fdTicketId") String fdTicketId) {
        return () -> {
            String escalationTime = slaService.escalate(fdTicketId);
            boolean success = (escalationTime != null && escalationTime.length() > SlaService.TimeLengthMin);
            JSONObject response = new JSONObject();
            response.put("success", success);
            response.put("time", escalationTime);
            return response.toString();
        };
    }

    @ApiOperation(value = "L1 Agent 응답 시간 업데이트", notes = "L1 Agent가 응답한 시간을 티켓에 기록합니다.")
    @ApiResponses(value = {
            @ApiResponse(code = 500, message = "INTERNAL_SERVER_ERROR"),
            @ApiResponse(code = 400, message = "BAD_REQUEST"),
            @ApiResponse(code = 401, message = "UNAUTHORIZED"),
            @ApiResponse(code = 404, message = "NOT_FOUND"),
            @ApiResponse(code = 409, message = "CONFLICT"),
            @ApiResponse(code = 200, message = "OK")
    })
    @PostMapping(path = "l1/ack/{fdTicketId}")
    public Callable<Object> l1Ack(@ApiParam(required = true, value = "fdTicketId: Freshdesk의 티켓 아이디 (필수 값)") @PathVariable("fdTicketId") String fdTicketId) {
        return () -> {
            String agentTime = slaService.l1Ack(fdTicketId);
            boolean success = (agentTime != null && agentTime.length() > SlaService.TimeLengthMin);
            JSONObject response = new JSONObject();
            response.put("success", success);
            response.put("time", agentTime);
            return response.toString();
        };
    }

    @ApiOperation(value = "L2 Agent에 이관된 시간 업데이트", notes = "L2 Agent에 이관된 시간을 티켓에 기록합니다.")
    @ApiResponses(value = {
            @ApiResponse(code = 500, message = "INTERNAL_SERVER_ERROR"),
            @ApiResponse(code = 400, message = "BAD_REQUEST"),
            @ApiResponse(code = 401, message = "UNAUTHORIZED"),
            @ApiResponse(code = 404, message = "NOT_FOUND"),
            @ApiResponse(code = 409, message = "CONFLICT"),
            @ApiResponse(code = 200, message = "OK")
    })
    @PostMapping(path = "l2/assign/{fdTicketId}")
    public Callable<Object> l2Assign(@ApiParam(required = true, value = "fdTicketId: Freshdesk의 티켓 아이디 (필수 값)") @PathVariable("fdTicketId") String fdTicketId) {
        return () -> {
            String agentTime = slaService.l2Assign(fdTicketId);
            boolean success = (agentTime != null && agentTime.length() > SlaService.TimeLengthMin);
            JSONObject response = new JSONObject();
            response.put("success", success);
            response.put("time", agentTime);
            return response.toString();
        };
    }

    @ApiOperation(value = "L2 Agent 응답 시간 업데이트", notes = "L2 Agent가 응답한 시간을 티켓에 기록합니다.")
    @ApiResponses(value = {
            @ApiResponse(code = 500, message = "INTERNAL_SERVER_ERROR"),
            @ApiResponse(code = 400, message = "BAD_REQUEST"),
            @ApiResponse(code = 401, message = "UNAUTHORIZED"),
            @ApiResponse(code = 404, message = "NOT_FOUND"),
            @ApiResponse(code = 409, message = "CONFLICT"),
            @ApiResponse(code = 200, message = "OK")
    })
    @PostMapping(path = "l2/ack/{fdTicketId}")
    public Callable<Object> l2Ack(@ApiParam(required = true, value = "fdTicketId: Freshdesk의 티켓 아이디 (필수 값)") @PathVariable("fdTicketId") String fdTicketId) {
        return () -> {
            String agentTime = slaService.l2Ack(fdTicketId);
            boolean success = (agentTime != null && agentTime.length() > SlaService.TimeLengthMin);
            JSONObject response = new JSONObject();
            response.put("success", success);
            response.put("time", agentTime);
            return response.toString();
        };
    }

    @ApiOperation(value = "티켓 서비스에 설정된 SLA 기본 대상 기간(2주)의 티켓에 대하여 전체 Agent의 SLA 초과 목록 조회", notes = "전체 Agent의 SLA를 초과한 티켓 목록을 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(code = 500, message = "INTERNAL_SERVER_ERROR"),
            @ApiResponse(code = 400, message = "BAD_REQUEST"),
            @ApiResponse(code = 401, message = "UNAUTHORIZED"),
            @ApiResponse(code = 404, message = "NOT_FOUND"),
            @ApiResponse(code = 409, message = "CONFLICT"),
            @ApiResponse(code = 200, message = "OK")
    })
    @GetMapping(path = "violations")
    public Callable<Object> listViolations(@RequestParam(name = "type", required = false, defaultValue = "opened") String type) {
        return () -> {
            log.debug("");
            JSONObject result = slaService.listViolations(TicketStatus.fromName(type));
            return JsonUtil.prettyPrint(result);
        };
    }

    @ApiOperation(value = "티켓 서비스에 설정된 SLA 기본 대상 기간(2주)의 티켓에 대하여 L1 Agent의 SLA 초과 목록 조회", notes = "L1 Agent의 SLA를 초과한 티켓 목록을 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(code = 500, message = "INTERNAL_SERVER_ERROR"),
            @ApiResponse(code = 400, message = "BAD_REQUEST"),
            @ApiResponse(code = 401, message = "UNAUTHORIZED"),
            @ApiResponse(code = 404, message = "NOT_FOUND"),
            @ApiResponse(code = 409, message = "CONFLICT"),
            @ApiResponse(code = 200, message = "OK")
    })
    @GetMapping(path = "violations/l1")
    public Callable<Object> listL1Violations(@RequestParam(name = "type", required = false, defaultValue = "opened") String type) {
        return () -> {
            log.debug("");
            JSONObject result = slaService.listL1Violations(TicketStatus.fromName(type));
            return JsonUtil.prettyPrint(result);
        };
    }

    @ApiOperation(value = "티켓 서비스에 설정된 SLA 기본 대상 기간(2주)의 티켓에 대하여 L2 Agent의 SLA 초과 목록 조회", notes = "L2 Agent의 SLA를 초과한 티켓 목록을 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(code = 500, message = "INTERNAL_SERVER_ERROR"),
            @ApiResponse(code = 400, message = "BAD_REQUEST"),
            @ApiResponse(code = 401, message = "UNAUTHORIZED"),
            @ApiResponse(code = 404, message = "NOT_FOUND"),
            @ApiResponse(code = 409, message = "CONFLICT"),
            @ApiResponse(code = 200, message = "OK")
    })
    @GetMapping(path = "violations/l2")
    public Callable<Object> listL2Violations(@RequestParam(name = "type", required = false, defaultValue = "opened") String type) {
        return () -> {
            log.debug("");
            JSONObject result = slaService.listL2Violations(TicketStatus.fromName(type));
            return JsonUtil.prettyPrint(result);
        };
    }

    @ApiOperation(value = "대상 기간을 지정하여 전체 Agent의 SLA 초과 목록 조회", notes = "전체 Agent의 SLA를 초과한 티켓 목록을 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(code = 500, message = "INTERNAL_SERVER_ERROR"),
            @ApiResponse(code = 400, message = "BAD_REQUEST"),
            @ApiResponse(code = 401, message = "UNAUTHORIZED"),
            @ApiResponse(code = 404, message = "NOT_FOUND"),
            @ApiResponse(code = 409, message = "CONFLICT"),
            @ApiResponse(code = 200, message = "OK")
    })
    @GetMapping(path = "violations/range")
    public Callable<Object> listViolations(SlaRequestParam param) {
        return () -> {
            adjustDate(param);
            log.debug("param: {}", param);
            JSONObject result = slaService.listViolations(param.getTargetPeriodStart(), param.getTargetPeriodEnd(), param.getTicketStatus());
            return JsonUtil.prettyPrint(result);
        };
    }

    @ApiOperation(value = "대상 기간을 지정하여 L1 Agent의 SLA 초과 목록 조회", notes = "L1 Agent의 SLA를 초과한 티켓 목록을 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(code = 500, message = "INTERNAL_SERVER_ERROR"),
            @ApiResponse(code = 400, message = "BAD_REQUEST"),
            @ApiResponse(code = 401, message = "UNAUTHORIZED"),
            @ApiResponse(code = 404, message = "NOT_FOUND"),
            @ApiResponse(code = 409, message = "CONFLICT"),
            @ApiResponse(code = 200, message = "OK")
    })
    @GetMapping(path = "violations/l1/range")
    public Callable<Object> listL1Violations(SlaRequestParam param) {
        return () -> {
            adjustDate(param);
            log.debug("param: {}", param);
            JSONObject result = slaService.listL1Violations(param.getTargetPeriodStart(), param.getTargetPeriodEnd(), param.getTicketStatus());
            return JsonUtil.prettyPrint(result);
        };
    }

    @ApiOperation(value = "대상 기간을 지정하여 L2 Agent의 SLA 초과 목록 조회", notes = "L2 Agent의 SLA를 초과한 티켓 목록을 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(code = 500, message = "INTERNAL_SERVER_ERROR"),
            @ApiResponse(code = 400, message = "BAD_REQUEST"),
            @ApiResponse(code = 401, message = "UNAUTHORIZED"),
            @ApiResponse(code = 404, message = "NOT_FOUND"),
            @ApiResponse(code = 409, message = "CONFLICT"),
            @ApiResponse(code = 200, message = "OK")
    })
    @GetMapping(path = "violations/l2/range")
    public Callable<Object> listL2Violations(SlaRequestParam param) {
        return () -> {
            adjustDate(param);
            log.debug("param: {}", param);
            JSONObject result = slaService.listL2Violations(param.getTargetPeriodStart(), param.getTargetPeriodEnd(), param.getTicketStatus());
            return JsonUtil.prettyPrint(result);
        };
    }

    private void adjustDate(SlaRequestParam param) {
        Calendar cal = Calendar.getInstance();
        Date dateFrom = param.getTargetPeriodStart();
        cal.setTimeInMillis(dateFrom.getTime());
        //Reset Time to maximum.
        Util.resetTimeToZero(cal);
        dateFrom.setTime(cal.getTimeInMillis());
        param.setTargetPeriodStart(dateFrom);

        Date dateTo = param.getTargetPeriodEnd();
        cal.setTimeInMillis(dateTo.getTime());
        //Reset Time to maximum.
        Util.resetTimeToMax(cal);
        dateTo.setTime(cal.getTimeInMillis());
        param.setTargetPeriodEnd(dateTo);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////
    //SLA Report API
    ////////////////////////////////////////////////////////////////////////////////////////////////////////
    @PostMapping("reports/start")
    public void startSlaReportAbility() throws AppError {
        log.info("@@@ START SLA Report Generator @@@");
        slaService.startSlaReportGenerator();
    }

    @PostMapping("reports/stop")
    public void stopSlaReportAbility() {
        log.info("@@@ STOP SLA Report Generator @@@");
        slaService.stopSlaReportGenerator();
    }

    @GetMapping("reports")
    public Callable<String> getSlaReports() {
        return () -> {
            return slaService.getSlaReports().toString();
        };
    }

    @PostMapping("reports")
    public Callable<String> createSlaReport(@RequestBody(required = false) SlaRequestParam param) {
        return () -> {
            log.info("parameter: {}", param);
            if (param != null && param.isValidRequest()) {
                return slaService.createSlaReport(param);
            } else {
                throw AppError.badRequest("Not available request parameters.");
            }
        };
    }

    @GetMapping("reports/{reportId}")
    public ResponseEntity<StreamingResponseBody> getSlaReport(@PathVariable("reportId") String reportId) throws AppError {
        return slaService.getSlaReport(reportId, SlaReport.ReportType.meta);
    }

    @GetMapping("reports/{reportId}/{type}")
    public ResponseEntity<StreamingResponseBody> getSlaReport(@PathVariable("reportId") String reportId, @PathVariable("type") String type) throws AppError {
        SlaReport.ReportType reportType;
        if (type == null || "".equals(type)) {
            reportType = SlaReport.ReportType.meta;
        } else {
            try {
                reportType = SlaReport.ReportType.valueOf(type);
            } catch (IllegalArgumentException e) {
                throw AppError.badRequest("Not supported report type.");
            }
        }
        return slaService.getSlaReport(reportId, reportType);
    }
}
