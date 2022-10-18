package com.sk.bds.ticket.api.controller;

import com.sk.bds.ticket.api.service.CloudZService;
import com.sk.bds.ticket.api.util.Util;
import io.swagger.annotations.*;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.Callable;

@Slf4j
@RestController
@RequestMapping(value = "cloudz")
public class CloudZController {
    private static final String CspSupplyCode = CloudZService.SupplyCode.SoftLayer;

    @PostConstruct
    public void init() {
    }

    @ApiOperation(value = "사용자 API 정보 조회", notes = "사용자의 Email 주소로 IBM 서비스를 사용하는 각 Account 별 Access key와 Secret key를 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(code = 500, message = "INTERNAL_SERVER_ERROR"),
            @ApiResponse(code = 400, message = "BAD_REQUEST"),
            @ApiResponse(code = 401, message = "UNAUTHORIZED"),
            @ApiResponse(code = 404, message = "NOT_FOUND"),
            @ApiResponse(code = 200, message = "OK")
    })
    @ApiImplicitParams({@ApiImplicitParam(name = "authorization", value = "API 인증 토큰 (필수 값)", required = true, dataType = "String", paramType = "header", defaultValue = "token")})
    @GetMapping(path = "getAccounts")
    //@GetMapping("")
    public Callable<Object> getAccounts(@ApiParam(required = true, value = "email: Account를 조회할 Email 주소 (필수 값)") @RequestParam String email) {
        return () -> {
            try {
                return CloudZService.getAcceptableUserApiInfoListByEmail(email).toString();
            } catch (IOException | URISyntaxException e) {
                Util.ignoreException(e);
            }
            return "[]";
        };
    }

    @ApiOperation(value = "CSP Account Id에 연관된 사용자 목록 조회", notes = "CSP 식별자와 Account Id로 사용자 목록을 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(code = 500, message = "INTERNAL_SERVER_ERROR"),
            @ApiResponse(code = 400, message = "BAD_REQUEST"),
            @ApiResponse(code = 401, message = "UNAUTHORIZED"),
            @ApiResponse(code = 404, message = "NOT_FOUND"),
            @ApiResponse(code = 200, message = "OK")
    })
    @ApiImplicitParams({@ApiImplicitParam(name = "authorization", value = "API 인증 토큰 (필수 값)", required = true, dataType = "String", paramType = "header", defaultValue = "token")})
    @GetMapping(path = "getAccountUsers")
    public Callable<Object> getAccountUsers(@RequestParam(name = "cloudSupplyCode", required = true, defaultValue = CspSupplyCode) String cloudSupplyCode, @RequestParam(name = "accountId", required = true, defaultValue = "") String accountId) {
        return () -> {
            try {
                JSONObject result = CloudZService.getAccountUsers(cloudSupplyCode, accountId);
                return result.toString();
            } catch (IOException | URISyntaxException e) {
                Util.ignoreException(e);
            }
            return "{}";
        };
    }

    @ApiOperation(value = "특정 CSP를 사용중인 모든 Account 목록 조회", notes = "CSP 식별자로 Account 목록을 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(code = 500, message = "INTERNAL_SERVER_ERROR"),
            @ApiResponse(code = 400, message = "BAD_REQUEST"),
            @ApiResponse(code = 401, message = "UNAUTHORIZED"),
            @ApiResponse(code = 404, message = "NOT_FOUND"),
            @ApiResponse(code = 200, message = "OK")
    })
    @ApiImplicitParams({@ApiImplicitParam(name = "authorization", value = "API 인증 토큰 (필수 값)", required = true, dataType = "String", paramType = "header", defaultValue = "token")})
    @GetMapping(path = "getAllAccounts")
    public Callable<Object> getAllAccounts(@RequestParam(name = "cloudSupplyCode", required = true, defaultValue = CspSupplyCode) String cloudSupplyCode) {
        return () -> {
            try {
                JSONObject result = CloudZService.getAllAccounts(cloudSupplyCode);
                return result.toString();
            } catch (IOException | URISyntaxException e) {
                Util.ignoreException(e);
            }
            return "{}";
        };
    }
}
