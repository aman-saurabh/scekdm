package com.tp.scekdm.config.twitter.to.kafka.service.listener;

import com.tp.scekdm.config.KafkaConfigData;
import com.tp.scekdm.config.twitter.to.kafka.service.transformer.TwitterStatusToAvroTransformer;
import com.tp.scekdm.kafka.avro.model.TwitterAvroModel;
import com.tp.scekdm.kafka.producer.config.service.KafkaProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import twitter4j.Status;
import twitter4j.StatusAdapter;

@Component
public class TwitterKafkaStatusListener extends StatusAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(TwitterKafkaStatusListener.class);
    private final KafkaConfigData kafkaConfigData;
    private final KafkaProducer<Long, TwitterAvroModel> kafkaProducer;
    private final TwitterStatusToAvroTransformer twitterStatusToAvroTransformer;

    public TwitterKafkaStatusListener(KafkaConfigData kafkaConfigData,
                                      KafkaProducer<Long, TwitterAvroModel> kafkaProducer,
                                      TwitterStatusToAvroTransformer twitterStatusToAvroTransformer) {
        this.kafkaConfigData = kafkaConfigData;
        this.kafkaProducer = kafkaProducer;
        this.twitterStatusToAvroTransformer = twitterStatusToAvroTransformer;
    }

    @Override
    public void onStatus(Status status){
        LOG.info("Twitter status with text {}, sending to kafka topic {}", status.getText(), kafkaConfigData.getTopicName());
        TwitterAvroModel twitterAvroModel = twitterStatusToAvroTransformer.getTwitterAvroModelFromStatus(status);
        //Here for key we used User ID from Twitter Avro model and for message we just sent the object Twitter Avro model
        //That means we want to partition the data using the userId field of Twitter Avro model object.
        //That way the Tweets that belongs to a user will be inserted to the same partition on the Kafka topic.
        kafkaProducer.send(kafkaConfigData.getTopicName(), twitterAvroModel.getUserId(), twitterAvroModel);
    }
}
