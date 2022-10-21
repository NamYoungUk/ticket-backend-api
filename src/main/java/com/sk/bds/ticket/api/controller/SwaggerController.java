package com.sk.bds.ticket.api.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SwaggerController {

    @GetMapping("/")
    public Object getRoot() {
        return "";
    }

    @GetMapping("/csrf")
    public Object getCSRF() {
        return "";
    }
}
