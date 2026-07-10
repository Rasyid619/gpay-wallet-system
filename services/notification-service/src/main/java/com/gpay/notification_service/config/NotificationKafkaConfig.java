package com.gpay.notification_service.config;

import com.gpay.notification_service.dto.PaymentTopupEventPayload;
import com.gpay.notification_service.dto.WalletTransferEventPayload;
import com.gpay.notification_service.exception.NonRetryableNotificationException;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties.AckMode;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Configures the Kafka consumers for transfer and top-up notification events,
 * including JSON deserialization and a dead-letter error handler that routes
 * non-retryable failures to {@code dead-letter.events}.
 */
@Configuration
public class NotificationKafkaConfig {

	private static final long RETRY_INTERVAL_MS = 2000L;

	/**
	 * Generous retry budget so a transient SMTP or auth-lookup fault recovers
	 * within the live flow instead of dead-lettering a customer notification
	 * after only a few seconds. Malformed events are classified non-retryable
	 * below and skip this budget entirely.
	 */
	private static final long MAX_RETRY_ATTEMPTS = 10L;

	@Value("${spring.kafka.bootstrap-servers}")
	private String bootstrapServers;

	@Value("${spring.kafka.consumer.group-id:notification-service}")
	private String groupId;

	@Bean
	public ConsumerFactory<String, WalletTransferEventPayload> transferEventConsumerFactory() {
		return new DefaultKafkaConsumerFactory<>(consumerConfig(WalletTransferEventPayload.class));
	}

	@Bean
	public ConcurrentKafkaListenerContainerFactory<String, WalletTransferEventPayload> transferEventListenerContainerFactory(
			ConsumerFactory<String, WalletTransferEventPayload> transferEventConsumerFactory,
			DefaultErrorHandler notificationErrorHandler) {
		ConcurrentKafkaListenerContainerFactory<String, WalletTransferEventPayload> factory =
				new ConcurrentKafkaListenerContainerFactory<>();
		factory.setConsumerFactory(transferEventConsumerFactory);
		factory.setCommonErrorHandler(notificationErrorHandler);
		factory.getContainerProperties().setAckMode(AckMode.RECORD);
		return factory;
	}

	@Bean
	public ConsumerFactory<String, PaymentTopupEventPayload> topupEventConsumerFactory() {
		return new DefaultKafkaConsumerFactory<>(consumerConfig(PaymentTopupEventPayload.class));
	}

	@Bean
	public ConcurrentKafkaListenerContainerFactory<String, PaymentTopupEventPayload> topupEventListenerContainerFactory(
			ConsumerFactory<String, PaymentTopupEventPayload> topupEventConsumerFactory,
			DefaultErrorHandler notificationErrorHandler) {
		ConcurrentKafkaListenerContainerFactory<String, PaymentTopupEventPayload> factory =
				new ConcurrentKafkaListenerContainerFactory<>();
		factory.setConsumerFactory(topupEventConsumerFactory);
		factory.setCommonErrorHandler(notificationErrorHandler);
		factory.getContainerProperties().setAckMode(AckMode.RECORD);
		return factory;
	}

	@Bean
	public ProducerFactory<Object, Object> deadLetterProducerFactory() {
		Map<String, Object> config = new HashMap<>();
		config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
		config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
		config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
		config.put(ProducerConfig.ACKS_CONFIG, "all");
		return new DefaultKafkaProducerFactory<>(config);
	}

	@Bean
	public KafkaTemplate<Object, Object> deadLetterKafkaTemplate(
			ProducerFactory<Object, Object> deadLetterProducerFactory) {
		return new KafkaTemplate<>(deadLetterProducerFactory);
	}

	@Bean
	public DefaultErrorHandler notificationErrorHandler(
			KafkaTemplate<Object, Object> deadLetterKafkaTemplate,
			NotificationKafkaProperties properties) {
		DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
				deadLetterKafkaTemplate,
				(record, exception) -> new TopicPartition(properties.deadLetterTopic(), record.partition()));
		DefaultErrorHandler errorHandler = new DefaultErrorHandler(
				recoverer,
				new FixedBackOff(RETRY_INTERVAL_MS, MAX_RETRY_ATTEMPTS));
		errorHandler.addNotRetryableExceptions(NonRetryableNotificationException.class);
		return errorHandler;
	}

	private Map<String, Object> consumerConfig(Class<?> defaultValueType) {
		Map<String, Object> config = new HashMap<>();
		config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
		config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
		config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
		config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
		config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
		config.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
		config.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
		config.put(JsonDeserializer.VALUE_DEFAULT_TYPE, defaultValueType.getName());
		config.put(JsonDeserializer.TRUSTED_PACKAGES, "com.gpay");
		config.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
		return config;
	}
}
