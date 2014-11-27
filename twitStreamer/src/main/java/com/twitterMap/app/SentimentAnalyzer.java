package com.twitMap.twitStreamer;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Map;
import java.util.Map.Entry;
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

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.DescribeTableRequest;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.PutItemResult;
import com.amazonaws.services.dynamodbv2.model.TableDescription;
import com.amazonaws.services.dynamodbv2.util.Tables;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
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
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageRequest;

public class SentimentAnalyzer{

	static AmazonDynamoDBClient dynamoDBClient = null;
    static AmazonSQS sqsClient = null;
    static AmazonSNS snsClient = null;

    
	public static void init() throws Exception{

        Region usEast1 = Region.getRegion(Regions.US_EAST_1);
    	AWSCredentials credentials = null;
    	
    	try{
    	    credentials = new ProfileCredentialsProvider("victor").getCredentials();
    	}catch( Exception e ){
    	    throw new AmazonClientException("Cannot load credentials from credentials profile file"+
    					    "Please make sure that your credentials file is at the correct"+
    					    "Location (/Users/wakahiu/.aws/credentials), and is in valid format"+e);
    	}
    	dynamoDBClient = new AmazonDynamoDBClient(credentials);
        dynamoDBClient.setRegion(usEast1);

        sqsClient = new AmazonSQSClient(credentials);    	
        sqsClient.setRegion(usEast1);

        snsClient = new AmazonSNSClient(credentials);
        snsClient.setRegion(usEast1);

    }

	public static void main(String [] argv) throws Exception {

		init();

		final String tableName = "TweetsTable3";
        final String sqsName = "twitsQueue";
        final String topicName = "tweetsTopic";

    	try{

            //--------------------------------------------------------------------
    	    /*
            * Create the table if it does not exist.
            */
            System.out.println("\n--------------------------------------------------------------------");
    	    if( !Tables.doesTableExist( dynamoDBClient , tableName ) ){
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

            // List queues
            System.out.println("Listing all queues in your account.");
            for (String queueUrl : sqsClient.listQueues().getQueueUrls()) {
                System.out.println("  QueueUrl: " + queueUrl);
            }
            System.out.println();
            //137.194.20.223
            //10.157.189.79:5000
            //--------------------------------------------------------------------
            /*
            * Create the notification topic if it does not exist.
            */
            System.out.println("\n--------------------------------------------------------------------");
            System.out.println("Creating a new SNS topic called " + topicName);
            //create a new SNS topic
            CreateTopicRequest createTopicRequest = new CreateTopicRequest(topicName);
            CreateTopicResult createTopicResult = snsClient.createTopic(createTopicRequest);
            final String topicARN = createTopicResult.getTopicArn();
            System.out.println(createTopicResult);


            //subscribe to an SNS topic
            String httpEndpoint = "http://ec2-54-174-110-195.compute-1.amazonaws.com:5000/stream";
            SubscribeRequest subscribeRequest = new SubscribeRequest(topicARN, "http", httpEndpoint);
            SubscribeResult  subscribeResult = snsClient.subscribe(subscribeRequest);

            //--------------------------------------------------------------------
            /*
            * Create the threadpool to analyze the sentiments.
            */
            ExecutorService executor = Executors.newFixedThreadPool(5);
          	// Receive messages
            System.out.println("Creating a threadpool to analyze sentiments. Tweets are pulled from queue: " + sqsName);

            int count = 0;
            while( true ){  //TODO check if the queue is empty.

                //count++;

                if(count > 3000){
                    System.exit(-1);
                }

                ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest(twitsQueueURL);
                List<Message> messages = sqsClient.receiveMessage(receiveMessageRequest).getMessages();
                Runnable worker = new WorkerThread(messages, snsClient, topicARN,dynamoDBClient, tableName);
                executor.execute(worker);
            }



        }catch (AmazonClientException ace) {
            System.out.println( "Caught an AmazonClientException, which means the client encountered "
                    + "a serious internal problem while trying to communicate with AWS, "
                    + "such as not being able to access the network.");
            System.out.println("Error Message: " + ace.getMessage());
        }

	}

}
