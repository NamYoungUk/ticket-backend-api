package com.sk.bds.ticket.api.data.model.ibm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sk.bds.ticket.api.service.IbmService;
import com.sk.bds.ticket.api.util.JsonUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Data
public class IbmCustomerCache {
    Map<String, IbmBrandAccount> brandAccounts;
    Map<String, List<IbmCustomer>> ibmCustomers;

    //ibm 전체 account 목록 //http://dev-bizapi.cloudz.co.kr:8080/account/csp/getAllAccountInfoList?cloudSupplyCode=01
    //"userType": "user" 인 계정들의 master 계정 목록 생성
    //master 계정 목록의 모든 api key를 해당 email에 참조하도록 맵 구성

    public IbmCustomerCache() {
        brandAccounts = new ConcurrentHashMap<>();
        ibmCustomers = new ConcurrentHashMap<>();
    }

    public void addBrand(IbmBrandAccount brand) {
        if (brand != null && brand.getBrandId() != null) {
            brandAccounts.put(brand.getBrandId(), brand);
            refresh(brand);
        }
    }

    public void removeBrand(String brandId) {
        if (brandId != null) {
            brandAccounts.remove(brandId);
            clearCustomers(brandId);
        }
    }

    public List<IbmCustomer> getBrandCustomers(String brandId) {
        if (brandId != null) {
            return ibmCustomers.get(brandId);
        }
        return null;
    }

    public IbmCustomer getIbmCustomer(String brandId, String customerId) {
        if (brandId != null) {
            List<IbmCustomer> brandCustomers = ibmCustomers.get(brandId);
            if (brandCustomers != null) {
                for (IbmCustomer customer : brandCustomers) {
                    if (customer.equalsId(customerId)) {
                        return customer;
                    }
                }
            }
        }
        return null;
    }

    public boolean isAvailable() {
        return (ibmCustomers.size() > 0);
    }

    public void clear() {
        brandAccounts.clear();
        ibmCustomers.clear();
    }

    public void clearCustomers(String brandId) {
        if (brandId != null) {
            List<IbmCustomer> brandCustomers = ibmCustomers.remove(brandId);
            if (brandCustomers != null) {
                brandCustomers.clear();
            }
        }
    }

    public void refresh() {
        clear();
        for (IbmBrandAccount brandAccount : brandAccounts.values()) {
            refresh(brandAccount);
        }
    }

    private void refresh(IbmBrandAccount brand) {
        if (brand != null && brand.getBrandId() != null) {
            clearCustomers(brand.getBrandId());
            List<IbmCustomer> brandCustomers = IbmService.getCustomerListOfBrand(brand);
            if (brandCustomers != null) {
                ibmCustomers.put(brand.getBrandId(), brandCustomers);
                log.info("brand: {} - {} customers found.", brand.getBrandId(), brandCustomers.size());
            } else {
                log.error("brand: {} - customer not found.", brand.getBrandId());
            }
        }
    }

    public JSONObject export() {
        try {
            String jsonText = JsonUtil.marshal(ibmCustomers);
            return new JSONObject(jsonText);
        } catch (JsonProcessingException e) {
            log.error("error:{}", e);
        }
        return new JSONObject();
    }
}
