package com.beep.jobportal.fetch;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * The public IPv4 address this server uses for outgoing calls.
 * Careerjet only accepts requests from declared IPs, so we need to know it,
 * and we also send it as Careerjet's required user_ip parameter for batch runs.
 */
@Component
public class EgressIp {

    private final RestClient http;

    public EgressIp(RestClient http) {
        this.http = http;
    }

    public String current() {
        String ip = http.get().uri("https://api.ipify.org").retrieve().body(String.class);
        return ip == null ? null : ip.strip();
    }
}
