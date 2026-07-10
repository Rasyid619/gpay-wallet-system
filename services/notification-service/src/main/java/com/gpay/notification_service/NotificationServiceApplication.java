package com.gpay.notification_service;

import com.gpay.notification_service.config.NotificationAuthProperties;
import com.gpay.notification_service.config.NotificationKafkaProperties;
import com.gpay.notification_service.config.NotificationMailProperties;
import com.gpay.notification_service.config.NotificationRetryProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/* Bootstraps the notification service application. */
@SpringBootApplication
@EnableConfigurationProperties({
		NotificationAuthProperties.class,
		NotificationKafkaProperties.class,
		NotificationMailProperties.class,
		NotificationRetryProperties.class
})
public class NotificationServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(NotificationServiceApplication.class, args);
	}
}
