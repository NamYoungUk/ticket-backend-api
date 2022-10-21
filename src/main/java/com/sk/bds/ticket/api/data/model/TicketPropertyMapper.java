package com.sk.bds.ticket.api.data.model;

import com.sk.bds.ticket.api.data.model.cloudz.CloudZCspApiInfo;
import com.sk.bds.ticket.api.data.model.ibm.IbmTicketCategory;
import com.softlayer.api.ApiClient;
import com.softlayer.api.service.ticket.Subject;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class TicketPropertyMapper {
    Map<String, Long> subjectMap;

    public TicketPropertyMapper() {
        subjectMap = new ConcurrentHashMap<>();
    }

    public TicketPropertyMapper(CloudZCspApiInfo cspApiInfo) {
        subjectMap = new ConcurrentHashMap<>();
        init(cspApiInfo);
    }

    public void init(CloudZCspApiInfo cspApiInfo) {
        if (cspApiInfo != null) {
            subjectMap.clear();
            ApiClient ibmClient = cspApiInfo.buildApiClient();
            if (ibmClient != null) {
                // Subject Info
                List<Subject> subjects = new Subject().asService(ibmClient).getAllObjects();
                for (Subject item : subjects) {
                    subjectMap.put(item.getName(), item.getId());
                }
            } else {
                log.error("Cannot build ApiClient. accountId:{}, accessKey:{}", cspApiInfo.getApiId(), cspApiInfo.coveredKey());
            }
        }
    }

    public long getSubjectIdByOffering(String offering) {
        if (subjectMap.containsKey(offering)) {
            return subjectMap.get(offering);
        }
        return 0;
    }

    public String getOfferingBySubjectId(long subjectId) {
        for (String offering : subjectMap.keySet()) {
            if (subjectMap.get(offering) == subjectId) {
                return offering;
            }
        }
        return null;
    }

    public String getSupportTypeBySubjectId(long subjectId) {
        for (String offering : subjectMap.keySet()) {
            if (subjectMap.get(offering) == subjectId) {
                return IbmTicketCategory.getGroupNameBySubjectName(offering);
            }
        }
        return null;
    }
}
