package com.tp.scekdm.config.twitter.to.kafka.service.runner.impl;

import com.tp.scekdm.config.TwitterToKafkaServiceConfigData;
import com.tp.scekdm.config.twitter.to.kafka.service.exception.TwitterToKafkaServiceException;
import com.tp.scekdm.config.twitter.to.kafka.service.listener.TwitterKafkaStatusListener;
import com.tp.scekdm.config.twitter.to.kafka.service.runner.StreamRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import twitter4j.Status;
import twitter4j.TwitterException;
import twitter4j.TwitterObjectFactory;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

@Component
//It will only load this class if enable-v2-tweets set to false and enable-mock-tweets set to true in the application configuration.
@ConditionalOnExpression("${twitter-to-kafka-service.enable-mock-tweets} && not ${twitter-to-kafka-service.enable-v2-tweets}")
public class MockKafkaStreamRunner implements StreamRunner {
    private static final Logger LOG = LoggerFactory.getLogger(MockKafkaStreamRunner.class);
    private final TwitterToKafkaServiceConfigData twitterToKafkaServiceConfigData;
    private final TwitterKafkaStatusListener twitterKafkaStatusListener;
    private static final Random RANDOM = new Random();
    private static final String[] WORDS = new String[] {
            "Lorem",
            "ipsum",
            "dollar",
            "sit",
            "consectetur",
            "adipiscing",
            "elit",
            "Vivamus",
            "sed",
            "sem",
            "neque",
            "egestas",
            "convallis",
            "Vestibulum",
            "vel",
            "feugiat",
            "enim",
            "vitae",
            "condimentum",
            "libero",
            "juilik",
            "setual"
    };

    //It creates JSON representation of a tweet similar to the Twitter4J library tweets.
    private static final String tweetAsRawJson = "{" +
            "\"created_at\":\"{0}\"," +
            "\"id\":\"{1}\"," +
            "\"text\":\"{2}\"," +
            "\"user\":{\"id\":\"{3}\"}" +
            "}";

    private static final String TWITTER_STATUS_DATE_FORMAT = "EEE MMM dd HH:mm:ss zzz yyyy";

    public MockKafkaStreamRunner(TwitterToKafkaServiceConfigData twitterToKafkaServiceConfigData,
                                 TwitterKafkaStatusListener twitterKafkaStatusListener) {
        this.twitterToKafkaServiceConfigData = twitterToKafkaServiceConfigData;
        this.twitterKafkaStatusListener = twitterKafkaStatusListener;
    }

    @Override
    public void start() throws TwitterException {
        String[] keywords = twitterToKafkaServiceConfigData.getTwitterKeywords().toArray(new String[0]);
        int minTweetLength = twitterToKafkaServiceConfigData.getMockMinTweetLength();
        int maxTweetLength = twitterToKafkaServiceConfigData.getMockMaxTweetLength();
        long sleepTimeMs = twitterToKafkaServiceConfigData.getMockSleepMs();
        LOG.info("Starting mock filtering twitter streams for keywords {}", Arrays.toString(keywords));
        simulateTwitterStream(keywords, minTweetLength, maxTweetLength, sleepTimeMs);
    }

    private void simulateTwitterStream(String[] keywords, int minTweetLength, int maxTweetLength, long sleepTimeMs) {
        //Running this task with a new thread, But here we have used Executors class inplace of Thread class for
        //creating a new thread. Thread class can only handle Runnable tasks, whereas
        //Executors.newSingleThreadExecutor().submit() can execute both Runnable and Callable tasks. Therefore, using
        //this, we can also run tasks that can return some value. Note that when we write this lambda inside the
        // submit() method, we implement the Runnable interface.
        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                while (true){
                    String formattedTweetAsRawJson = getFormattedTweet(keywords, minTweetLength, maxTweetLength);
                    //We are calling TwitterObjectFactory createStatus() method and assign the result to local Status variable.
                    Status status = TwitterObjectFactory.createStatus(formattedTweetAsRawJson);
                    twitterKafkaStatusListener.onStatus(status);
                    sleep(sleepTimeMs);
                }
            } catch (TwitterException e) {
                LOG.error("Error in creating twitter status", e);
            }
        });
    }

    private void sleep(long sleepTimeMs) {
        try {
            Thread.sleep(sleepTimeMs);
        } catch (InterruptedException e){
            throw new TwitterToKafkaServiceException("Error while in sleep mode in MockKafkaStreamRunner");
        }
    }

    private String getFormattedTweet(String[] keywords, int minTweetLength, int maxTweetLength) {
        //Creating a String array for the parameters of our Tweet object.
        String[] params = new String[] {
                //As first parameter, we will set the current time with Zone, using the format we defined for twitter
                //stream. We will use ZoneDateTime now() method for that, and use DateFormatter to format the date.
                ZonedDateTime.now().format(DateTimeFormatter.ofPattern(TWITTER_STATUS_DATE_FORMAT, Locale.ENGLISH)),
                //Then as second parameter we will use ThreadLocalRandom to get a random long number, to set the ID field of tweet.
                //There is no nextLong with an upper bound in standard Random class, So we use this thread safe
                //ThreadLocalRandom class, to create a random Long number.
                String.valueOf(ThreadLocalRandom.current().nextLong(Long.MAX_VALUE)),
                //For the third parameter i.e. tweet text, let's create another method called getRandomTweetContent,
                //which we have implemented below this method.
                getRandomTweetContent(keywords, minTweetLength, maxTweetLength),
                //For the userID, we can use the same method which we used for 'id' i.e. nextLong() method from
                //ThreadLocalRandom class.
                String.valueOf(ThreadLocalRandom.current().nextLong(Long.MAX_VALUE))
        };
        //Now, let's write the code to create the tweets. We will first copy the original template, so that we can
        //update it here locally, and then create a simple loop to iterate over parameters. Note that in the 'params'
        //we had first entered time, then id, then tweet text and then userId because in the tweet template we have
        // set the value for "created_at" as {0}, "id" as {1}, "text" as {2} and "user" as {3}. So we will replace
        // these values with corresponding params values in this loop.
        String tweet = tweetAsRawJson;
        for(int i = 0; i < params.length; i++){
            //replacing template values(i.e. {1}, {2} etc.) with corresponding params values.
            tweet = tweet.replace("{"+i+"}", params[i]);
        }
        return tweet;
    }

    private String getRandomTweetContent(String[] keywords, int minTweetLength, int maxTweetLength) {
        StringBuilder tweet = new StringBuilder();
        //It returns a random number between 0 and given upper bound. Since upper bound is exclusive, so we have added 1.
        int tweetLength = RANDOM.nextInt(maxTweetLength - minTweetLength + 1) + minTweetLength;
        //To create the tweet, we will simply get random words from lorem ipsum array, until we reach to length/2 in
        //the loop. And then we will add a random keyword from our keyword list and finally rest will be filled
        //with random words from lorem ipsum array again.
        for (int i =0; i<tweetLength; i++){
            String randomWord = WORDS[RANDOM.nextInt(WORDS.length)];
            tweet.append(randomWord).append(" ");
            //In the middle of the tweet text adding a keyword from "keywords" array.
            if(i == tweetLength/2){
                String randomKeyword = keywords[RANDOM.nextInt(keywords.length)];
                tweet.append(randomKeyword).append(" ");
            }
        }
        //Calling the trim() method to remove the unnecessary white space at the end.
        return tweet.toString().trim();
    }
}
