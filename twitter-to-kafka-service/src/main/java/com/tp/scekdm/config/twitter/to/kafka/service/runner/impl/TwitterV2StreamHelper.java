package com.tp.scekdm.config.twitter.to.kafka.service.runner.impl;

/*
    After Twitter introduced v2 API Twitter4 J library stopped working for new developer accounts. As you see with a
    new Twitter developer account, I get this error while trying to stream tweets from the Twitter API.

    message - You currently have Essential access which includes access to Twitter API v2 endpoints only. If you need
    access to this endpoint, you’ll need to apply for Elevated access via the Developer Portal. You can learn more
    here: https://developer.twitter.com/en/docs/twitter-api/getting-started/about-twitter-api#v2-access-leve
    code - 453

    And in the error it is suggested to use V2 filtered and sample volume stream as alternatives. For this reason, We
    will now write a custom twitter stream client to be able to stream data from Twitter V2 API. We will still keep the
    Twitter4J code and write the new code as a new implementation of the StreamRunner interface and trigger
    Twitter4J listener class.

    For reference, you can refer to following link -
    https://github.com/twitterdev/Twitter-API-v2-sample-code/blob/main/Filtered-Stream/FilteredStreamDemo.java

    So here we are creating a mirror of the twitter official example(defined in above URL), and we are not writing it
    from scratch. So we have just copied the content from above URL and updated it a little to fulfill our requirement.
 */
import com.tp.scekdm.config.TwitterToKafkaServiceConfigData;
import com.tp.scekdm.config.twitter.to.kafka.service.listener.TwitterKafkaStatusListener;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import twitter4j.Status;
import twitter4j.TwitterException;
import twitter4j.TwitterObjectFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@ConditionalOnExpression("${twitter-to-kafka-service.enable-v2-tweets} && not ${twitter-to-kafka-service.enable-mock-tweets}")
public class TwitterV2StreamHelper {

    private static final Logger LOG = LoggerFactory.getLogger(TwitterV2StreamHelper.class);

    private final TwitterToKafkaServiceConfigData twitterToKafkaServiceConfigData;

    private final TwitterKafkaStatusListener twitterKafkaStatusListener;

    //It creates JSON representation of a tweet similar to the Twitter4J library tweets.
    private static final String tweetAsRawJson = "{" +
            "\"created_at\":\"{0}\"," +
            "\"id\":\"{1}\"," +
            "\"text\":\"{2}\"," +
            "\"user\":{\"id\":\"{3}\"}" +
            "}";

    //To format the tweet date correctly.
    private static final String TWITTER_STATUS_DATE_FORMAT = "EEE MMM dd HH:mm:ss zzz yyyy";

    public TwitterV2StreamHelper(TwitterToKafkaServiceConfigData twitterToKafkaServiceConfigData,
                                 TwitterKafkaStatusListener twitterKafkaStatusListener) {
        this.twitterToKafkaServiceConfigData = twitterToKafkaServiceConfigData;
        this.twitterKafkaStatusListener = twitterKafkaStatusListener;
    }

    /*
     * This method calls the filtered stream endpoint and streams Tweets from it
     * */
    void connectStream(String bearerToken) throws IOException, URISyntaxException, JSONException {

        HttpClient httpClient = HttpClients.custom()
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setCookieSpec(CookieSpecs.STANDARD).build())
                .build();

        URIBuilder uriBuilder = new URIBuilder(twitterToKafkaServiceConfigData.getTwitterV2BaseUrl());

        HttpGet httpGet = new HttpGet(uriBuilder.build());
        httpGet.setHeader("Authorization", String.format("Bearer %s", bearerToken));

        HttpResponse response = httpClient.execute(httpGet);
        HttpEntity entity = response.getEntity();
        if (null != entity) {
            BufferedReader reader = new BufferedReader(new InputStreamReader((entity.getContent())));
            String line = reader.readLine();
            while (line != null) {
                line = reader.readLine();
                if (!line.isEmpty()) {
                    String tweet = getFormattedTweet(line);
                    Status status = null;
                    try {
                        //Here we are creating Twitter4J status object using Twitter Object Factory createStatus method.
                        status = TwitterObjectFactory.createStatus(tweet);
                    } catch (TwitterException e) {
                        LOG.error("Could not create status for text: {}", tweet, e);
                    }
                    if (status != null) {
                        //Finally, we call the Twitter Kafka Status Listener onStatus method.
                        //It is to ensure that we can still use the same way to consume the tweets are we were doing
                        //earlier i.e. with Twitter4j library.
                        twitterKafkaStatusListener.onStatus(status);
                    }
                }
            }
        }
    }

    /*
     * Helper method to setup rules before streaming data
     * */
    void setupRules(String bearerToken, Map<String, String> rules) throws IOException, URISyntaxException {
        List<String> existingRules = getRules(bearerToken);
        if (existingRules.size() > 0) {
            deleteRules(bearerToken, existingRules);
        }
        createRules(bearerToken, rules);
        LOG.info("Created rules for twitter stream {}", rules.keySet().toArray());
    }


    /*
     * Helper method to create rules for filtering
     * */
    private void createRules(String bearerToken, Map<String, String> rules) throws URISyntaxException, IOException {
        HttpClient httpClient = HttpClients.custom()
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setCookieSpec(CookieSpecs.STANDARD).build())
                .build();

        URIBuilder uriBuilder = new URIBuilder(twitterToKafkaServiceConfigData.getTwitterV2RulesBaseUrl());

        HttpPost httpPost = new HttpPost(uriBuilder.build());
        httpPost.setHeader("Authorization", String.format("Bearer %s", bearerToken));
        httpPost.setHeader("content-type", "application/json");
        StringEntity body = new StringEntity(getFormattedString("{\"add\": [%s]}", rules));
        httpPost.setEntity(body);
        HttpResponse response = httpClient.execute(httpPost);
        HttpEntity entity = response.getEntity();
        if (null != entity) {
            System.out.println(EntityUtils.toString(entity, "UTF-8"));
        }
    }

    /*
     * Helper method to get existing rules
     * */
    private List<String> getRules(String bearerToken) throws URISyntaxException, IOException {
        List<String> rules = new ArrayList<>();
        HttpClient httpClient = HttpClients.custom()
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setCookieSpec(CookieSpecs.STANDARD).build())
                .build();

        URIBuilder uriBuilder = new URIBuilder(twitterToKafkaServiceConfigData.getTwitterV2RulesBaseUrl());

        HttpGet httpGet = new HttpGet(uriBuilder.build());
        httpGet.setHeader("Authorization", String.format("Bearer %s", bearerToken));
        httpGet.setHeader("content-type", "application/json");
        HttpResponse response = httpClient.execute(httpGet);
        HttpEntity entity = response.getEntity();
        if (null != entity) {
            JSONObject json = new JSONObject(EntityUtils.toString(entity, "UTF-8"));
            if (json.length() > 1 && json.has("data")) {
                JSONArray array = (JSONArray) json.get("data");
                for (int i = 0; i < array.length(); i++) {
                    JSONObject jsonObject = (JSONObject) array.get(i);
                    rules.add(jsonObject.getString("id"));
                }
            }
        }
        return rules;
    }

    /*
     * Helper method to delete rules
     * */
    private void deleteRules(String bearerToken, List<String> existingRules) throws URISyntaxException, IOException {
        HttpClient httpClient = HttpClients.custom()
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setCookieSpec(CookieSpecs.STANDARD).build())
                .build();

        URIBuilder uriBuilder = new URIBuilder(twitterToKafkaServiceConfigData.getTwitterV2RulesBaseUrl());

        HttpPost httpPost = new HttpPost(uriBuilder.build());
        httpPost.setHeader("Authorization", String.format("Bearer %s", bearerToken));
        httpPost.setHeader("content-type", "application/json");
        StringEntity body = new StringEntity(getFormattedString("{ \"delete\": { \"ids\": [%s]}}",
                existingRules));
        httpPost.setEntity(body);
        HttpResponse response = httpClient.execute(httpPost);
        HttpEntity entity = response.getEntity();
        if (null != entity) {
            System.out.println(EntityUtils.toString(entity, "UTF-8"));
        }
    }

    private String getFormattedString(String string, List<String> ids) {
        StringBuilder sb = new StringBuilder();
        if (ids.size() == 1) {
            return String.format(string, "\"" + ids.get(0) + "\"");
        } else {
            for (String id : ids) {
                sb.append("\"" + id + "\"" + ",");
            }
            String result = sb.toString();
            return String.format(string, result.substring(0, result.length() - 1));
        }
    }

    private String getFormattedString(String string, Map<String, String> rules) {
        StringBuilder sb = new StringBuilder();
        if (rules.size() == 1) {
            String key = rules.keySet().iterator().next();
            return String.format(string, "{\"value\": \"" + key + "\", \"tag\": \"" + rules.get(key) + "\"}");
        } else {
            for (Map.Entry<String, String> entry : rules.entrySet()) {
                String value = entry.getKey();
                String tag = entry.getValue();
                sb.append("{\"value\": \"" + value + "\", \"tag\": \"" + tag + "\"}" + ",");
            }
            String result = sb.toString();
            return String.format(string, result.substring(0, result.length() - 1));
        }
    }

    /*
    This method is called from "connectStream" method to format the tweet in the desired format.
    In this method we extracted tweet data And get createdAt, id, text and authorId values.
    As you see, we format the date using the correct formatter and also escape the double quotes in the tweet text.
    Then we use the tweetAsRawJson variable to create the exact same tweet message as in the Twitter4J.
    */
    private String getFormattedTweet(String data) {
        JSONObject jsonData = (JSONObject)new JSONObject(data).get("data");

        String[] params = new String[]{
                ZonedDateTime.parse(jsonData.get("created_at").toString()).withZoneSameInstant(ZoneId.of("UTC"))
                        .format(DateTimeFormatter.ofPattern(TWITTER_STATUS_DATE_FORMAT, Locale.ENGLISH)),
                jsonData.get("id").toString(),
                jsonData.get("text").toString().replaceAll("\"","\\\\\""),
                jsonData.get("author_id").toString(),
        };
        return formatTweetAsJsonWithParams(params);
    }

    private String formatTweetAsJsonWithParams(String[] params) {
        String tweet = tweetAsRawJson;

        for (int i = 0; i < params.length; i++) {
            tweet = tweet.replace("{" + i + "}", params[i]);
        }
        return tweet;
    }
}
