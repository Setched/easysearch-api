package me.setched.easysearch.api.api.controller;

import me.setched.easysearch.api.api.dto.PingResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PingController {

    @GetMapping("/api/ping")
    public PingResponse ping() {
        return new PingResponse("ok");
    }
}
