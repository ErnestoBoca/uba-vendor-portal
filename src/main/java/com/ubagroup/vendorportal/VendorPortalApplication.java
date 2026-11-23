package com.ubagroup.vendorportal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Date;

@SpringBootApplication
public class VendorPortalApplication {
    public static void main(String[] args) {
        SpringApplication.run(VendorPortalApplication.class, args);
        System.out.println("\n===============================================================================================================\n");
        System.out.println("                     VENDOR-PORTAL STARTED RUNNING ON " + new Date());
        System.out.println("\n===============================================================================================================\n");
    }
}
