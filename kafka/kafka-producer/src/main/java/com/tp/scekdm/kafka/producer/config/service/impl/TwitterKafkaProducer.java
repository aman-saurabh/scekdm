package com.tp.scekdm.kafka.producer.config.service.impl;

import com.google.common.util.concurrent.ListenableFuture;
import com.tp.scekdm.kafka.avro.model.TwitterAvroModel;
import com.tp.scekdm.kafka.producer.config.service.KafkaProducer;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

//For the generic variables replacements of KafkaProducer interface, we are using Long and TwitterAvroModel.
@Service
public class TwitterKafkaProducer implements KafkaProducer<Long, TwitterAvroModel> {
    private static final Logger LOG = LoggerFactory.getLogger(TwitterKafkaProducer.class);

    private final KafkaTemplate<Long, TwitterAvroModel> kafkaTemplate;

    public TwitterKafkaProducer(KafkaTemplate<Long, TwitterAvroModel> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /*
        Normally spring closes this Kafka template prior to shutdown. I just added this method to call the Kafka
        template destroy method also explicitly to be sure that it is destroyed successfully before application shut down.
     */
    public void close(){
        if(kafkaTemplate != null){
            LOG.info("Closing kafka producer");
            kafkaTemplate.destroy();
        }
    }

    @Override
    public void send(String topicName, Long key, TwitterAvroModel message) {
        LOG.info("Sending message {} to topic {}", message, topicName);
        CompletableFuture<SendResult<Long, TwitterAvroModel>> kafkaResultFuture =
                kafkaTemplate.send(topicName, key, message);
        kafkaResultFuture.whenComplete((response, error) -> addCallback(topicName, message, response, error));
    }

    private static void addCallback(String topicName, TwitterAvroModel message, SendResult<Long, TwitterAvroModel> response, Throwable error) {
        if(error != null){
            LOG.error("Error while sending message {} to topic {}", message, topicName);
        } else {
            RecordMetadata metadata = response.getRecordMetadata();
            LOG.debug("Received new metadata. Topic {}; Partition: {}; Offset: {}; at time {}",
                    metadata.topic(),
                    metadata.partition(),
                    metadata.offset(),
                    System.nanoTime() );
        }
    }
}
