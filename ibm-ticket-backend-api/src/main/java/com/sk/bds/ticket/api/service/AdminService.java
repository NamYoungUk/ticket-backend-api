package com.sk.bds.ticket.api.service;

import com.sk.bds.ticket.api.data.model.AppConstants;
import com.sk.bds.ticket.api.data.model.PropertyReader;
import com.sk.bds.ticket.api.data.model.TicketMetadata;
import com.sk.bds.ticket.api.data.model.cloudz.CloudZCspApiInfo;
import com.sk.bds.ticket.api.data.model.freshdesk.FreshdeskCspAccountField;
import com.sk.bds.ticket.api.data.model.freshdesk.FreshdeskTicketField;
import com.sk.bds.ticket.api.data.model.freshdesk.FreshdeskTicketResponse;
import com.sk.bds.ticket.api.data.model.freshdesk.FreshdeskTicketStatus;
import com.sk.bds.ticket.api.util.TicketUtil;
import com.softlayer.api.ApiClient;
import com.softlayer.api.service.Ticket;
import com.softlayer.api.service.ticket.Update;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

@Slf4j
@Service
public class AdminService {

    @Autowired
    PropertyReader propertyReader;

    /**
     * freshdeskTicketId를 가지고, cspCaseId를 조회하는 테스트
     * @param freshdeskTicketId
     * @return
     * @throws Exception
     */
    public JSONObject checkCspTicket(String freshdeskTicketId) throws Exception {

        String KEY = "ERROR";
        JSONObject o = new JSONObject();

        String freshdeskServicePortalEndpoint = propertyReader.getFreshdeskServicePortalEndpoint();
        o.put("freshdeskServicePortalEndpoint", freshdeskServicePortalEndpoint);

//        String supplyCode = CloudZService.SupplyCode.SoftLayer;
//        JSONObject allAccounts = CloudZService.getAllAccounts(supplyCode);
//        if(allAccounts == null || allAccounts.isEmpty()) {
//            o.put(KEY, "allAccounts is empty");
//            return o;
//        }

        FreshdeskTicketResponse ticketResponse = FreshdeskService.getTicket(freshdeskTicketId);
        JSONObject freshdeskTicketData = ticketResponse.getResponseBody();
        if (freshdeskTicketData == null) {
            // 응답헤더의 내용을 티켓에 추가되는 에러 노트와 OG Alert에 추가할 필요는 없고 로그에만 찍히도록 하면 될 것 같습니다.
            // 에러 노트는 Support Portal 운영자, 에이전트 들이 보는 내용이라 굳이 표시하지 않아도 될 듯 합니다. by 김민정 @2022-07-22
            // String xRequestId = FreshdeskService.getXRequestId(ticketResponse);
            o.put(KEY, "freshdeskTicketData is null");
            return o;
        }

        if (!freshdeskTicketData.has(FreshdeskTicketField.CustomFields)) {
            o.put(KEY, "freshdeskTicketData do not have customFields");
            return o;
        }

        int fdTicketStatus = freshdeskTicketData.optInt(FreshdeskTicketField.Status);
        String ticketStatus = FreshdeskTicketStatus.toString(fdTicketStatus);

        JSONObject customData = freshdeskTicketData.getJSONObject(FreshdeskTicketField.CustomFields);
        if (customData == null) {
            o.put(KEY, "customData is null");
            return o;
        }

        String csp = customData.optString(FreshdeskTicketField.CfCsp);
        if (!TicketUtil.isValidCsp(csp)) {
            o.put(KEY, "This ticket is not IBM Ticket.");
            return o;
        }

        String cspAccount = customData.optString(FreshdeskTicketField.CfCspAccount);
        if (StringUtils.isEmpty(cspAccount)) {
            o.put(KEY, "cspAccount is Empty");
            return o;
        }

        String escalation = customData.optString(FreshdeskTicketField.CfEscalation);
        FreshdeskCspAccountField accountField = FreshdeskCspAccountField.from(cspAccount);
        if (!accountField.isValid()) {
            o.put(KEY, "invalid freshdeskCspAccountField");
            return o;
        }

        String cspAccountId = accountField.getAccountId();
        if(StringUtils.isEmpty(cspAccountId)) {
            o.put(KEY, "cspAccountId is Empty");
            return o;
        }

        // cspTicketIdRequired == true인 조건
//        boolean cspTicketIdRequired = "Open".equals(ticketStatus) && !StringUtils.isEmpty(cspAccountId) && "Y".equals(escalation);
//        if (!cspTicketIdRequired) {
//            o.put(KEY, String.format("cspTicketIdRequired is False, ticketStatus:%s, cspAccountId:%s, escalation:%s"
//                    , ticketStatus, cspAccountId, escalation));
//            return o;
//        }

        boolean cspTicketRequired = "Y".equals(escalation)?true:false;
        TicketMetadata ticketMetadata = TicketMetadata.build(freshdeskTicketData, cspTicketRequired); //operator.getMonitoringTicket(freshdeskTicketId);
        if (ticketMetadata == null) {
            o.put(KEY, "ticketMetadata is null, build fail");
            return o;
        }

        // 직접 조회 시점에는 정상이나, 문제 발생한 시점의 accountCache에는 없는 경우가 있다.
        CloudZCspApiInfo cspApiInfo = CloudZService.getCspApiInfo(ticketMetadata.getCspAccountEmail(), ticketMetadata.getCspAccountId());
        if (cspApiInfo == null) {
            o.put(KEY, "cspApiInfo is null");
            return o;
        }

        // String cspCaseId = customData.optString(FreshdeskTicketField.CfCspCaseId);
        String cspCaseId = ticketMetadata.getCspTicketId();
        if (StringUtils.isEmpty(cspCaseId) && cspTicketRequired == true) {
            // [Internal Error] Invalid ticket metadata의 주된 원인
            // cspCaseId값을 입력하지 않고, escalation == Y 로 설정한 경우
            o.put(KEY, "cspCaseId is Empty, Invalid ticket metadata for cspTicketRequired");
            return o;
        }

        if (!StringUtils.equals(escalation, "Y")) {
            JSONObject result = new JSONObject();
            result.put("escalation", escalation);
            o.put("SUCCESS", result);
            return o;
        }

        ApiClient ibmClient = cspApiInfo.buildApiClient();
        if  (ibmClient == null) {
            o.put(KEY, "ibmClient is null");
            return o;
        }

        Ticket.Service ibmTicketService = Ticket.service(ibmClient, Long.valueOf(cspCaseId));
        if (ibmTicketService == null) {
            o.put(KEY, "ibmTicketService is Empty");
            return o;
        }

        Update firstUpdate = null;
        String error = null;
        try {
            firstUpdate = ibmTicketService.getFirstUpdate();
        } catch (com.softlayer.api.ApiException e) {
            error = String.format("firstUpdate is %s", e.getMessage());
            log.error("{}", error);
        } finally {
            if (firstUpdate == null) {
                o.put(KEY, StringUtils.isEmpty(error)? "firstUpdate is null" : error);
                return o;
            }
        }

        com.softlayer.api.service.ticket.Status ibmTicketStatus = ibmTicketService.getStatus();
        if (ibmTicketStatus == null) {
            o.put(KEY, "ibmTicketStatus is null");
            return o;
        }

        String cspStatus = ibmTicketStatus.getName();
        Long cspTicketId = firstUpdate.getTicketId();
        String cspEntry = firstUpdate.getEntry();
        Date cspCreateDate = firstUpdate.getCreateDate().getTime();
        DateFormat localTimeFormat = new SimpleDateFormat(AppConstants.LOCAL_TIME_FORMAT);
        localTimeFormat.setTimeZone(AppConstants.getLocalTimeZone());

        JSONObject result = new JSONObject();
        result.put("escalation", escalation);
        result.put("cspTicketId", cspTicketId);
        result.put("cspStatus", cspStatus);
        result.put("cspCreateDate", localTimeFormat.format(cspCreateDate));
        result.put("cspEntry", cspEntry);
        // result.put("allAccounts", allAccounts);

        o.put("SUCCESS", result);

        return o;
    }

}
