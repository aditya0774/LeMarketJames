package com.lemarketjames;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
@SpringBootApplication
@EnableScheduling
public class OrdersServiceApplication {
    public static void main(String[] args) { SpringApplication.run(OrdersServiceApplication.class, args); }
}
