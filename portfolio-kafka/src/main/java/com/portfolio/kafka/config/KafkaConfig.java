package com.portfolio.kafka.config;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.JsonSerializer;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
@Slf4j
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:am-portfolio-group}")
    private String groupId;

    // Prefer KAFKA_*/PROJ_KAFKA_* (Vault + application.yml). Dotted
    // spring.kafka.properties.sasl.* @Value paths do not bind from the
    // "[sasl.jaas.config]" map keys, so consumers used to skip SASL and
    // get dropped by the broker (METADATA during SASL handshake).
    @Value("${KAFKA_SECURITY_PROTOCOL:${PROJ_KAFKA_SECURITY_PROTOCOL:}}")
    private String securityProtocol;

    @Value("${KAFKA_SASL_MECHANISM:${PROJ_KAFKA_SASL_MECHANISM:}}")
    private String saslMechanism;

    @Value("${KAFKA_JAAS_CONFIG:${PROJ_KAFKA_JAAS_CONFIG:}}")
    private String jaasConfig;

    // @Value("${app.kafka.topic}")
    // private String topicName;

    // @Bean
    // public NewTopic createTopic() {
    //     return new NewTopic(topicName, 1, (short) 1);
    // }

    @Bean
    public Map<String, Object> kafkaConfigs() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        if (jaasConfig != null && !jaasConfig.isBlank()) {
            props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, securityProtocol);
            props.put(SaslConfigs.SASL_MECHANISM, saslMechanism);
            props.put(SaslConfigs.SASL_JAAS_CONFIG, jaasConfig);
            log.info("Kafka SASL enabled protocol={} mechanism={}", securityProtocol, saslMechanism);
        } else {
            log.warn("Kafka SASL skipped — KAFKA_JAAS_CONFIG / PROJ_KAFKA_JAAS_CONFIG empty");
        }
        return props;
    }

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configProps = kafkaConfigs();
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        KafkaTemplate<String, Object> template = new KafkaTemplate<>(producerFactory());
        template.setObservationEnabled(true);
        return template;
    }

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> props = kafkaConfigs();
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        

        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.getContainerProperties().setObservationEnabled(true);
        return factory;
    }
}
