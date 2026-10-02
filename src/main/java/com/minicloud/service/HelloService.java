package com.minicloud.service;

import org.springframework.stereotype.Service;

@Service
public class HelloService {

    public String getMessage() {
        return "Mini Cloud is running!";
    }
}