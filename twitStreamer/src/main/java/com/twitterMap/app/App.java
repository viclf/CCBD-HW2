package com.twitMap.tweetStreamer;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.DescribeTableRequest;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.PutItemResult;
import com.amazonaws.services.dynamodbv2.model.TableDescription;
import com.amazonaws.services.dynamodbv2.util.Tables;


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

import twitter4j.*;

/**
 * Hello world!
 *
 */
public class App 
{

    static AmazonDynamoDBClient dynamoDBClient = null;

    public static void init() throws Exception{
        AWSCredentials credentials = null;
        
        try{
            credentials = new ProfileCredentialsProvider("victor").getCredentials();
        }catch( Exception e ){
            throw new AmazonClientException("Cannot load credentials from credentials profile file"+
                            "Please make sure that your credentials file is at the correct"+
                            "Location (/Users/wakahiu/.aws/credentials), and is in valid format"+e);
        }
        dynamoDBClient = new AmazonDynamoDBClient(credentials);
        Region usEast1 = Region.getRegion(Regions.US_EAST_1);
        dynamoDBClient.setRegion(usEast1);
    }

    public static void insertIntoDynamoDB(Status status, String tableName){
        Map<String,AttributeValue> item = new HashMap<String,AttributeValue>();
            
        GeoLocation geoLoc = status.getGeoLocation();
        
        item.put( "TweetID" , new AttributeValue().withS( "" + status.getId( ) ));
        item.put( "time", new AttributeValue().withS( status.getCreatedAt().toString() ));
        item.put( "latitude" , new AttributeValue().withN( "" + geoLoc.getLatitude()  ));
        item.put( "longitude", new AttributeValue().withN( "" + geoLoc.getLongitude() ));
        item.put( "userID", new AttributeValue().withN( "" + status.getUser().getId() ));
        item.put( "screenName", new AttributeValue().withS( status.getUser().getScreenName() ));
        item.put( "text" , new AttributeValue().withS( status.getText() ));

        PutItemRequest putItemRequest = new PutItemRequest()
                            .withTableName(tableName)
                            .withItem(item);
        System.out.println(putItemRequest);
/*
        GetItemRequest getItemRequest = new GetItemRequest()
                    .withKey(key)
                    .withTableName(tableName);

        GetItemResult getItemResult = dynamoDBClient.getItem(getItemRequest);
        Map<String,AttributeValue> itemMap = getItemResult.getItem();
*/        
        
        PutItemResult putItemResult = dynamoDBClient.putItem(putItemRequest);
        System.out.println(dynamoDBClient);
    }

    public static void logTweetStatus(Status status){
        PrintWriter out = null;
        try{
            GeoLocation geoLoc = status.getGeoLocation();

            String filePath = "/home/ec2-user/projects/tweetMap/tempTweetStream.txt";
            out = new PrintWriter( new BufferedWriter( new FileWriter( filePath , true )));
            
            out.print( status.getId() + ", ");
            out.print( geoLoc.getLatitude() + ", " );
            out.print( geoLoc.getLongitude() + ", ");
            out.print( status.getUser().getId() + ", ");
            out.print( status.getUser().getScreenName() + ", ");
            out.print( status.getText() );
            out.println();
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
    
    final String tableName = "TweetsTable2";
    
    try{

        //Create the table if it does not exist.
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
        System.out.println("Table Descrition");
        System.out.println(tableDescription);
    
    }catch (AmazonClientException ace) {
        System.out.println( "Caught an AmazonClientException, which means the client encountered "
                + "a serious internal problem while trying to communicate with AWS, "
                + "such as not being able to access the network.");
        System.out.println("Error Message: " + ace.getMessage());
    }
    
    TwitterStream twitterStream = new TwitterStreamFactory().getInstance();

        StatusListener listener = new StatusListener() {
            @Override
            public void onStatus(Status status) {
                GeoLocation geoLoc = status.getGeoLocation();
                Place place = status .getPlace();

                if(geoLoc != null ){
                    System.out.println("\n\n");
                    System.out.println("Geo @" + status.getUser().getScreenName() + " - " + status.getText() );
                    System.out.println("ID " + status.getId() );
                    System.out.println("Latitude " + geoLoc.getLatitude() + " Longitude "  + geoLoc.getLongitude() + status.getUser().getScreenName()  );
                    System.out.println("\n\n"); 
                    
                    //logTweetStatus(status);

                    insertIntoDynamoDB(status, tableName);

                }
            }

            @Override
            public void onDeletionNotice(StatusDeletionNotice statusDeletionNotice) {
                System.out.println("Got a status deletion notice id:" + statusDeletionNotice.getStatusId());
                //deleteFromDynamoDB( statusDeletionNotice );
            }

            @Override
            public void onTrackLimitationNotice(int numberOfLimitedStatuses) {
                System.out.println("Got track limitation notice:" + numberOfLimitedStatuses);
            }

            @Override
            public void onScrubGeo(long userId, long upToStatusId) {
                //System.out.println("Got scrub_geo event userId:" + userId + " upToStatusId:" + upToStatusId);
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
    }
}