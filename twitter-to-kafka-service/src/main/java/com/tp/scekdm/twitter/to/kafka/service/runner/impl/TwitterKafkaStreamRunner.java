package com.tp.scekdm.twitter.to.kafka.service.runner.impl;

import com.tp.scekdm.twitter.to.kafka.service.config.TwitterToKafkaServiceConfigData;
import com.tp.scekdm.twitter.to.kafka.service.listener.TwitterKafkaStatusListener;
import com.tp.scekdm.twitter.to.kafka.service.runner.StreamRunner;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import twitter4j.FilterQuery;
import twitter4j.TwitterException;
import twitter4j.TwitterStream;
import twitter4j.TwitterStreamFactory;

import java.util.Arrays;

@Component
//It will only load this class if enable-v2-tweets set to true in the application configuration.
//@ConditionalOnProperty(name="twitter-to-kafka-service.enable-v2-tweets", havingValue = "false")
@ConditionalOnExpression("${twitter-to-kafka-service.enable-mock-tweets} && not ${twitter-to-kafka-service.enable-v2-tweets}")

public class TwitterKafkaStreamRunner implements StreamRunner {
    private final Logger LOG = LoggerFactory.getLogger(TwitterKafkaStatusListener.class);
    private final TwitterToKafkaServiceConfigData twitterToKafkaServiceConfigData;
    private final TwitterKafkaStatusListener twitterKafkaStatusListener;

    private TwitterStream twitterStream;

    public TwitterKafkaStreamRunner(TwitterToKafkaServiceConfigData twitterToKafkaServiceConfigData,
                                    TwitterKafkaStatusListener twitterKafkaStatusListener) {
        this.twitterToKafkaServiceConfigData = twitterToKafkaServiceConfigData;
        this.twitterKafkaStatusListener = twitterKafkaStatusListener;
    }

    @Override
    public void start() throws TwitterException {
        //Creating twitter stream, using TwitterSteamFactory class getInstance() method.
        twitterStream = new TwitterStreamFactory().getInstance();
        //Adding the listener we just injected(i.e. twitterKafkaStatusListener listener) into this Twitter Stream
        twitterStream.addListener(twitterKafkaStatusListener);
        addFilter();
    }

    private void addFilter() {
        //Extracting the keywords as String array from the twitterToKafkaServiceConfigData object using toArray() method.
        String[] keywords = twitterToKafkaServiceConfigData.getTwitterKeywords().toArray(new String[0]);
        //Create a filter query from Twitter4J library with the above keywords array.
        FilterQuery filterQuery = new FilterQuery(keywords);
        //Then we will simply add the filter query to the twitter stream.
        twitterStream.filter(filterQuery);
        LOG.info("Started filtering twitter stream for keywords {}", Arrays.toString(keywords));
    }

    @PreDestroy
    private void shutdown(){
        //To ensure that Stream connection is closed prior to application close.
        if(twitterStream != null){
            twitterStream.shutdown();
        }
    }
    //Finally, let's inject this stream runner into the main class(i.e. in TwitterToKafkaServiceApplication class),
    //and call its start method in the run method of TwitterToKafkaServiceApplication class. And this way, we will
    //trigger the streaming logic on application start, and it will listen Twitter messages continuously for us.



}
