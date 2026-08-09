package me.setched.easysearch.api.web.controller;

import me.setched.easysearch.api.web.dto.PingResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A trivial health-check endpoint.
 */
@RestController
public class PingController {

    /**
     * @return a static "ok" status
     */
    @GetMapping("/api/ping")
    public PingResponse ping() {
        return new PingResponse("ok");
    }
}
