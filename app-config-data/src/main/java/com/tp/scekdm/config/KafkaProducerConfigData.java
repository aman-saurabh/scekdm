package com.tp.scekdm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "kafka-producer-config")
//To increase throughput of kafka we can increase the batched data in request. This can be done by increasing the batch
//size, adding a compression (as batching is done after compression), and increase the linger ms to add a delay on
//producer client to wait more and send more data at once.
public class KafkaProducerConfigData {
    private String keySerializerClass;
    private String valueSerializerClass;
    private String compressionType;
    //Here "acks" stands for acknowledgement, we used this property to get acknowledgement from kafka brokers.
    private String acks;
    private Integer batchSize;
    private Integer batchSizeBoostFactor;
    //linger.ms refers to the time to wait before sending messages out to Kafka. It defaults to 0, which the system
    //interprets as ‘send messages as soon as they are ready to be sent’. For light load we still want to create more
    //batches, so here we added a delay to producer client to wait before sending the records to Kafka.
    //Kafka producers will send out the next batch of messages whenever linger.ms or batch.size is met first.
    private Integer lingerMs;
    private Integer requestTimeoutMs;
    private Integer retryCount;
}
