package com.tp.scekdm.kafka.to.elastic.service.consumer.impl;

import com.tp.scekdm.config.KafkaConfigData;
import com.tp.scekdm.kafka.admin.client.KafkaAdminClient;
import com.tp.scekdm.kafka.avro.model.TwitterAvroModel;
import com.tp.scekdm.kafka.to.elastic.service.consumer.KafkaConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TwitterKafkaConsumer implements KafkaConsumer<Long, TwitterAvroModel> {
    private static final Logger LOG = LoggerFactory.getLogger(TwitterKafkaConsumer.class);
    private final KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;
    private final KafkaAdminClient kafkaAdminClient;
    private final KafkaConfigData kafkaConfigData;

    public TwitterKafkaConsumer(KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry, KafkaAdminClient kafkaAdminClient, KafkaConfigData kafkaConfigData) {
        this.kafkaListenerEndpointRegistry = kafkaListenerEndpointRegistry;
        this.kafkaAdminClient = kafkaAdminClient;
        this.kafkaConfigData = kafkaConfigData;
    }

    //Remember that @EventListener is one of the ways of running a code on application start.
    @EventListener
    public void onAppStarted(ApplicationStartedEvent event) {
        //Here we will do some checks to be able to sure that Kafka topics are created.So let's call
        //'kafkaAdminClient.checkTopicsCreated()' method.
        kafkaAdminClient.checkTopicsCreated();
        //Then log that, topics with the name specified in the configuration are ready for operations.
        LOG.info("Topics with name {} is ready for operations!", kafkaConfigData.getTopicNamesToCreate().toArray());
        //Finally, we will get the listener container from the id that we specified in the @KafkaListener annotation
        //and start it explicitly.
        kafkaListenerEndpointRegistry.getListenerContainer("twitterTopicListener").start();
        //Here note that we are starting kafka listener manually after checking kafka topics are created, so we should
        //set 'auto-startup' in 'kafka-consumer-config' section as false in 'config-client-kafka_to_elastic.yml' file,
        //otherwise kafka consumer will start listening even before kafka topics are created and can cause problems.
        //So we should set 'auto-startup' in 'kafka-consumer-config' section as false in
        //'config-client-kafka_to_elastic.yml' file.
    }

    @Override
    @KafkaListener(id="twitterTopicListener", topics = "${kafka-config.topic-name}")
    //Setting an id for kafka listener, like 'twitterTopicListener' and specify the topic. For topic we will use a
    //replacement with $ and curly braces for 'kafka-config.topic-name' by getting it from the configuration file of
    //kafka-to-elastic-service i.e. from "config-client-kafka_to_elastic.yml".
    public void receive(
            //Here as a parameter we have the payload as messages, so we need to annotate this as Payload.And for keys,
            //partitions and offsets Since they are coming from the Kafka headers, we will annotate them with Header
            //and specify the key by using Kafka Headers constants, like Received message key, Received Partition ID
            //and offset.
            @Payload List<TwitterAvroModel> messages,
            @Header(KafkaHeaders.RECEIVED_KEY) List<Integer> keys,
            @Header(KafkaHeaders.RECEIVED_PARTITION) List<Integer> partitions,
            @Header(KafkaHeaders.OFFSET) List<Long> offsets) {
        LOG.info("{} number of message received with keys {}, partitions {} and offsets {}, " +
                        "sending it to elastic: Thread id {}",
                messages.size(),
                keys.toString(),
                partitions.toString(),
                offsets.toString(),
                Thread.currentThread().getId());
    }
}
