package com.twitMap.twitStreamer;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.io.Writer;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.FileWriter;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.DescribeTableRequest;
import com.amazonaws.services.dynamodbv2.model.GetItemRequest;
import com.amazonaws.services.dynamodbv2.model.GetItemResult;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.PutItemResult;
import com.amazonaws.services.dynamodbv2.model.TableDescription;
import com.amazonaws.services.dynamodbv2.util.Tables;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.CreateTopicRequest;
import com.amazonaws.services.sns.model.CreateTopicResult;
import com.amazonaws.services.sns.model.GetSubscriptionAttributesResult;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.PublishResult;
import com.amazonaws.services.sns.model.SubscribeRequest;
import com.amazonaws.services.sns.model.SubscribeResult;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.CreateQueueResult;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;

import twitter4j.*;

/**
 * Hello world!
 *
 */
public class TwitStreamer 
{

    static AmazonDynamoDBClient dynamoDBClient = null;
    static AmazonSQS sqsClient = null;

    public static void init(){

        Region usEast1 = Region.getRegion(Regions.US_EAST_1);
    	AWSCredentials credentials = null;
    	
    	try{
    	    credentials = new ProfileCredentialsProvider("victor").getCredentials();
    	}catch( Exception e ){
    	    System.out.println("Cannot load credentials from credentials profile file"+
    					    "Please make sure that your credentials file is at the correct"+
    					    "Location (/Users/wakahiu/.aws/credentials), and is in valid format"+e);
            System.exit(-1);
    	}
    	dynamoDBClient = new AmazonDynamoDBClient(credentials);
        dynamoDBClient.setRegion(usEast1);

        sqsClient = new AmazonSQSClient(credentials);    	
        sqsClient.setRegion(usEast1);

    }

    public static void insertIntoDynamoDB(Status status, String tableName){
    	Map<String,AttributeValue> item = new HashMap<String,AttributeValue>();

        JSONObject statusObj = statusToJSON(status); 

        String tweetID = "" + statusObj.get("TweetID");
        String time = (String)statusObj.get("time");
        String latitude = "" + statusObj.get("latitude");
        String longitude = "" + statusObj.get("longitude");
        String userID = ""+statusObj.get("userID");
        String screenName = (String)statusObj.get("screenName");
        String text = (String)statusObj.get("text");

        item.put( "TweetID" , new AttributeValue().withS( tweetID ) );
        item.put( "time"  , new AttributeValue().withS( time ) );
        item.put( "latitude" ,  new AttributeValue().withN( latitude ) );
        item.put( "longitude" , new AttributeValue().withN( longitude ) );
        item.put( "userID" ,  new AttributeValue().withN( userID ) );
        item.put( "screenName" , new AttributeValue().withS( screenName ) );
        item.put( "text" , new AttributeValue().withS( text ) );
        item.put( "status" , new AttributeValue().withS( "NONE" ));
        item.put( "score" , new AttributeValue().withN( "-1" ));
        item.put( "type" , new AttributeValue().withS( "NONE" ));

        Map.Entry<String,AttributeValue> hashKey;
        hashKey = new SimpleImmutableEntry<String,AttributeValue>("TweetID",new AttributeValue().withS( tweetID ));
        Map.Entry<String,AttributeValue> rangeKey;
        rangeKey = new SimpleImmutableEntry<String,AttributeValue>("time",new AttributeValue().withS( time ));
    		
        GeoLocation geoLoc = status.getGeoLocation();

    	PutItemRequest putItemRequest = new PutItemRequest()
    					    .withTableName(tableName)
    					    .withItem(item);

    	PutItemResult putItemResult = dynamoDBClient.putItem(putItemRequest);

        GetItemRequest getItemRequest = new GetItemRequest()
                .withTableName(tableName)
                .withKey(hashKey,rangeKey)
                .withConsistentRead(true);

        GetItemResult getItemResult = dynamoDBClient.getItem(getItemRequest);
        Map<String,AttributeValue> itemMap = getItemResult.getItem();

        System.out.println();
        for( String attribute : itemMap.keySet() ){
            AttributeValue attrVal = itemMap.get(attribute);
            System.out.println( attribute + " " + attrVal);
        }


    }

    public static JSONObject statusToJSON(Status status){

        JSONObject statusObj = new JSONObject();
        GeoLocation geoLoc = status.getGeoLocation();

        try{

            statusObj.put( "TweetID" , status.getId( ) );
            statusObj.put( "time", status.getCreatedAt().toString() );
            statusObj.put( "userID",  status.getUser().getId() );
            statusObj.put( "screenName",  status.getUser().getScreenName() );
            statusObj.put( "text" ,  status.getText() );
            statusObj.put( "language" ,  status.getLang() );

            if(geoLoc != null){
                statusObj.put( "latitude" ,  geoLoc.getLatitude()  );
                statusObj.put( "longitude", geoLoc.getLongitude() );
            }else{
                statusObj.put( "latitude" ,  "-1" );
                statusObj.put( "longitude", "-1" );              
            }

        }catch(Exception e ){
            System.out.println(e );
            e.printStackTrace();
        }

        return statusObj;
    }

    public static void insertIntoQueue (Status status, String queueUrl){
            //Send message to Queue
            JSONObject statusObj = statusToJSON(status);
            sqsClient.sendMessage( new SendMessageRequest(queueUrl , statusObj.toString( ) ) );
    }

    public static void logTweetStatus(Status status){


        PrintWriter out = null;
        try{

            JSONObject statusObj = statusToJSON(status);
            //System.out.print( statusObj);

            String filePath = "/Users/wakahiu/Documents/school/CCBD/twitterMap/twitStreamer/tempTweetStream.txt";
            out = new PrintWriter( new BufferedWriter( new FileWriter( filePath , true )));
            
            out.println( statusObj);
            out.close();
        }catch(IOException ioe ){
            System.err.println(ioe);
        }finally{
            if(out != null){
                out.close();
            }
        }
    }
    
    public static void main(String[] args) throws TwitterException, Exception {
       
    	init();
    	
    	final String tableName = "TweetsTable3";
        final String sqsName = "twitsQueue";

    	try{

            //--------------------------------------------------------------------
    	    /*
            * Create the table if it does not exist.
            */
            System.out.println("\n--------------------------------------------------------------------");
    	    if( Tables.doesTableExist( dynamoDBClient , tableName ) ){
    		  System.out.println( "Table " + tableName + " is already Active!" );
    	    }else{
        		//TODO create a table if it does not exist
        		throw new Exception("Table does not exist!");
    	    }
    	    /*
    	    * Describe the table
    	    */
    	    DescribeTableRequest describeTableRequest  = new DescribeTableRequest().withTableName(tableName);
    	    TableDescription tableDescription  = dynamoDBClient.describeTable(describeTableRequest).getTable();
    	    System.out.println(tableDescription);
            System.out.println();

            //--------------------------------------------------------------------
            /*
            * Create the Queue if it does not exist.
            */
            System.out.println("\n--------------------------------------------------------------------");
            System.out.println("Creating a new SQS queue called " + sqsName);
            CreateQueueRequest createQueueRequest = new CreateQueueRequest(sqsName);
            CreateQueueResult createQueueResult = sqsClient.createQueue(createQueueRequest);
            final String twitsQueueURL = createQueueResult.getQueueUrl();

            //sqsClient.deleteQueue(twitsQueueURL);

            // List queues
            System.out.println("Listing all queues in your account.");
            for (String queueUrl : sqsClient.listQueues().getQueueUrls()) {
                System.out.println("  QueueUrl: " + queueUrl);
            }
            
    	   TwitterStream twitterStream = new TwitterStreamFactory().getInstance();

            StatusListener listener = new StatusListener() {
                
                @Override
                public void onStatus(Status status) {
                    GeoLocation geoLoc = status.getGeoLocation();
                    if(geoLoc != null ){     
                        logTweetStatus(status);
    		    		insertIntoDynamoDB(status, tableName);
                        insertIntoQueue(status,twitsQueueURL);
                    }
                }

                @Override
                public void onDeletionNotice(StatusDeletionNotice statusDeletionNotice) {
                    //System.out.println("Got a status deletion notice id:" + statusDeletionNotice.getStatusId());
    				//deleteFromDynamoDB( statusDeletionNotice );
                }

                @Override
                public void onTrackLimitationNotice(int numberOfLimitedStatuses) {
                    System.out.println("Got track limitation notice:" + numberOfLimitedStatuses);
                }

                @Override
                public void onScrubGeo(long userId, long upToStatusId) {
                    System.out.println("Got scrub_geo event userId:" + userId + " upToStatusId:" + upToStatusId);
                }

                @Override
                public void onStallWarning(StallWarning warning) {
    		      System.out.println("Got stall warning:" + warning);
                }

                @Override
                public void onException(Exception ex) {
                    ex.printStackTrace();
                }
            };
            twitterStream.addListener(listener);
            twitterStream.sample();

        }catch (AmazonClientException ace) {
            System.out.println( "Caught an AmazonClientException, which means the client encountered "
                    + "a serious internal problem while trying to communicate with AWS, "
                    + "such as not being able to access the network.");
            System.out.println("Error Message: " + ace.getMessage());
        }
    }
}
