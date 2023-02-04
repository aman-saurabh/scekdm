package com.tp.scekdm.kafka.admin.client;

import com.tp.scekdm.config.KafkaConfigData;
import com.tp.scekdm.config.RetryConfigData;
import com.tp.scekdm.kafka.admin.exception.KafkaClientException;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.TopicListing;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.retry.RetryContext;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.ClientResponse;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

/*
    Here we will implement a Kafka admin module using the "AdminClient" class from Apache Kafka Library, which is the
    administrative clients for Kafka. You will create Kafka topics in this module and also will check if the topics are
    created and schema server is running prior to running microservices. This will be required because when you run all
    components in a single Docker compose file. If Kafka or schema registry or any other dependencies are not running,
    your application will fail at startup and will not continue to work as expected. So to make our services more
    resilient, we will need to add some checks prior to running services. And in this module, we will implement these
    checks. In the coming section, we will be adding more checks on the docker composed file, apart from these
    programmatic checks, so that our services will be more and more resilient.

    In this module, we will target to create Kafka topics automatically. And also, we will write a logic to check if the
    topics are created. So that we can use these module to be sure topics are created and ready to be used by the
    application.
 */
@Component
public class KafkaAdminClient {
    private static final Logger LOG = LoggerFactory.getLogger(KafkaAdminClient.class);
    private final KafkaConfigData kafkaConfigData;
    private final RetryConfigData retryConfigData;
    private final AdminClient adminClient;
    private final RetryTemplate retryTemplate;
    private final WebClient webClient;

    public KafkaAdminClient(KafkaConfigData kafkaConfigData, RetryConfigData retryConfigData,
                            AdminClient adminClient, RetryTemplate retryTemplate, WebClient webClient) {
        this.kafkaConfigData = kafkaConfigData;
        this.retryConfigData = retryConfigData;
        this.adminClient = adminClient;
        this.retryTemplate = retryTemplate;
        this.webClient = webClient;
    }

    /*
        To create topics
     */
    public void createTopics() {
        CreateTopicsResult createTopicsResult;
        try {
            createTopicsResult = retryTemplate.execute(this::doCreateTopics);
        } catch (Throwable t) {
            throw new RuntimeException("Reached max numbers of retries for creating kafka topics", t);
        }
        checkTopicsCreated();
    }

    private CreateTopicsResult doCreateTopics(RetryContext retryContext) {
        List<String> topicNames = kafkaConfigData.getTopicNamesToCreate();
        LOG.info("Creating {} topic(s), attempt {}", topicNames.size(), retryContext.getRetryCount());
        List<NewTopic> kafkaTopics = topicNames.stream().map(topic -> new NewTopic(
                topic.trim(),
                kafkaConfigData.getNumOfPartitions(),
                kafkaConfigData.getReplicationFactor()
        )).toList();
        return adminClient.createTopics(kafkaTopics);
    }

    private Collection<TopicListing> getTopics() {
        Collection<TopicListing> topics;
        try {
            topics = retryTemplate.execute(this::doGetTopics);
        } catch (Throwable t) {
            throw new RuntimeException("Reached max numbers of retries for reading kafka topics", t);
        }
        return topics;
    }

    private Collection<TopicListing> doGetTopics(RetryContext retryContext) throws ExecutionException, InterruptedException {
        LOG.info("Creating kafka topic {}, attempt {}", kafkaConfigData.getTopicNamesToCreate(),
                retryContext.getRetryCount());
        Collection<TopicListing> topics = adminClient.listTopics().listings().get();
        if (topics != null) {
            topics.forEach(topic -> LOG.info("Topic with name {} is read", topic.name()));
        }
        return topics;
    }

    /*
        To check if topics are created.
     */
    public void checkTopicsCreated() {
        Collection<TopicListing> topics = getTopics();
        int retryCount = 1;
        Integer maxRetry = retryConfigData.getMaxAttempts();
        int multiplier = retryConfigData.getMultiplier().intValue();
        Long sleepTimeMs = retryConfigData.getSleepTimeMs();
        for (String topic : kafkaConfigData.getTopicNamesToCreate()) {
            // Because the kafka create topics is an asynchronous operation. That's why we added a custom retry logic on
            // top of the retry template here and we try to check until a maximum retry time and then exponentially
            // increase the sleep time until we get the topics using the getTopics() method.
            while (!isTopicCreated(topics, topic)) {
                checkMaxRetry(retryCount++, maxRetry);
                sleep(sleepTimeMs);
                sleepTimeMs *= multiplier;
                topics = getTopics();
            }
        }
    }

    private boolean isTopicCreated(Collection<TopicListing> topics, String topicName) {
        if (topics == null) {
            return false;
        }
        return topics.stream().anyMatch(topic -> topic.name().equals(topicName));
    }

    private void checkMaxRetry(int retry, Integer maxRetry) {
        if (retry > maxRetry) {
            throw new KafkaClientException("Reached max number of retry for reading kafka topic(s)!");
        }
    }

    private void sleep(Long sleepTimeMs) {
        try {
            Thread.sleep(sleepTimeMs);
        } catch (InterruptedException e) {
            throw new KafkaClientException("Error while sleeping for waiting new created topics!!");
        }
    }

    /*
        To check if schema registry is up and running.

        We don't want to fail at startup because schema registry is
        unreachable, so we will add a check for schema registry also here. To do that, we need to make a rest call to
        the schema registry endpoint. here we will also implement the same logic as we did in the checkTopicsCreated()
        method. Use the max retry multiplier and sleep time milliseconds configuration variables.
     */
    public void checkSchemaRegistry() {
        int retryCount = 1;
        Integer maxRetry = retryConfigData.getMaxAttempts();
        int multiplier = retryConfigData.getMultiplier().intValue();
        Long sleepTimeMs = retryConfigData.getSleepTimeMs();
        while (!getSchemaRegistryStatus().is2xxSuccessful()) {
            checkMaxRetry(retryCount++, maxRetry);
            sleep(sleepTimeMs);
            sleepTimeMs *= multiplier;
        }
    }

    /*
        We are making a rest call here and return the HTTP status to check the status of the schema registry. As a http
        method we will use get. For url, we will pass the schema registry url from the configuration variable, and we
        will call the exchange() method. Then we will map the response to a ClientResponse statusCode object. And
        finally block the operation to be able to get the results synchronously from schema registry.
     */

    private HttpStatusCode getSchemaRegistryStatus() {
        try {
            return webClient
                    .method(HttpMethod.GET)
                    .uri(kafkaConfigData.getSchemaRegistryUrl())
                    .retrieve()
                    .toBodilessEntity().block().getStatusCode();
        } catch (Exception e) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
    }
}
