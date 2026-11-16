package com.beep.jobportal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class JobPortalApplication {

    public static void main(String[] args) {
        // Use IPv4 for outgoing calls. Careerjet only allows declared IPv4 addresses;
        // on a machine with IPv6 the request would otherwise leave from an undeclared address.
        System.setProperty("java.net.preferIPv4Stack", "true");
        SpringApplication.run(JobPortalApplication.class, args);
    }
}
