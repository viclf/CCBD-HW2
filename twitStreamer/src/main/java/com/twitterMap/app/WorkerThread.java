package com.twitMap.twitStreamer;

import java.io.BufferedReader;	
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.InterruptedException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.List;


import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.GetItemRequest;
import com.amazonaws.services.dynamodbv2.model.GetItemResult;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import com.amazonaws.services.dynamodbv2.model.PutItemResult;
import com.amazonaws.services.dynamodbv2.model.ReturnValue;
import com.amazonaws.services.dynamodbv2.model.UpdateItemResult;
import com.amazonaws.services.dynamodbv2.model.UpdateItemRequest;
import com.amazonaws.services.dynamodbv2.model.UpdateItemResult;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.PublishResult;



import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;



import java.lang.Math;


import com.amazonaws.services.sqs.model.Message;

public class WorkerThread implements Runnable{

	private List<Message> messages;
	static AmazonSNS snsClient = null;
	private String topicARN = null;
	static AmazonDynamoDBClient dynamoDBClient = null;
	private String tableName = "";

	public WorkerThread(List<Message> messages, AmazonSNS snsClient, String topicARN, AmazonDynamoDBClient dynamoDBClient,String tableName){
		this.messages = messages;
		this.snsClient= snsClient;
		this.topicARN = topicARN;
		this.dynamoDBClient = dynamoDBClient;
		this.tableName = tableName;
	}

	@Override
	public void run(){
		try{

			HttpClient client = new DefaultHttpClient();
			HttpPost post = new HttpPost("http://access.alchemyapi.com/calls/text/TextGetTextSentiment");
			List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>();
			nameValuePairs.add(new BasicNameValuePair("apikey","f30f6dc9eb1500344fdce198c183394417db8fc7"));	//TODO move this to a file.
			nameValuePairs.add(new BasicNameValuePair("outputMode","json"));
			nameValuePairs.add(new BasicNameValuePair("showSourceText","1"));

		  	for (Message message : messages) {


		  		JSONParser jsonParser = new JSONParser();
		  		JSONObject jsonObject = (JSONObject)jsonParser.parse(message.getBody());

		  		String lang = (String)jsonObject.get("language");
		  		String text = (String)jsonObject.get("text");

		  		//Perform sentiment analysis for select languages. English, French and Spanish.
		  		if( lang.equals("en") || lang.equals("es") || lang.equals("fr") ){

		  			String uriEncodedText =  java.net.URLEncoder.encode( text, "ISO-8859-1");
		  			nameValuePairs.add(new BasicNameValuePair("text", uriEncodedText ));

		  			post.setEntity( new UrlEncodedFormEntity(nameValuePairs));
			  		HttpResponse response = client.execute(post);

			  		BufferedReader rd =  new BufferedReader( new InputStreamReader(response.getEntity().getContent()));

			  		String line;

			  		String sentimentResponse = "";

			  		while( (line = rd.readLine()) != null ){
			  			sentimentResponse += line;
			  		}

		  			JSONObject sentimentJsonObject = (JSONObject)jsonParser.parse(sentimentResponse);

		  			String status = (String)sentimentJsonObject.get("status");
		  			
		  			jsonObject.put("status",status);

		  			if(status.equals("OK")){
		  				JSONObject docSentiment = (JSONObject)sentimentJsonObject.get("docSentiment");
		  				if( ((String)docSentiment.get("type")).equals("neutral") ){
		  					docSentiment.put("score",0);
		  				}
		  				jsonObject.put("docSentiment",docSentiment.toString());
		  			}else{

		  				JSONObject docSentiment = new JSONObject();
		  				docSentiment.put("type","NONE");
		  				docSentiment.put("score",-1);
		  				jsonObject.put("docSentiment",docSentiment.toString());
		  			}
		  			updateDynamoDB(jsonObject, tableName);
		  			
		  		}else{
		  			
		  			jsonObject.put("status","NONE");
		  			JSONObject docSentiment = new JSONObject();
		  			docSentiment.put("type","NONE");
		  			docSentiment.put("score",-1);
		  			jsonObject.put("docSentiment",docSentiment.toString());
		  		}
		  		
		  		publishToSNStopic(jsonObject,topicARN);
		  		//System.out.println(jsonObject);
            }

		}catch( IOException ioE){
			System.out.println("Caught IOException " + ioE);
			ioE.printStackTrace();
			System.exit(-1);
		}catch( ParseException pE){
			System.out.println("Caught JSON ParseException " + pE);
			pE.printStackTrace();
			System.exit(-1);
		}
	}

	public static void publishToSNStopic (JSONObject statusObj, String topicARN){
            //Send message to Queue
            //System.out.println("Sending notification to endpoint");

            PublishRequest publishRequest = new PublishRequest(topicARN, statusObj.toString( ));
            PublishResult publishResult = snsClient.publish(publishRequest);

            //System.out.println(statusObj);
    }

    public static void updateDynamoDB(JSONObject statusObj, String tableName){

    	try{

    		/*
    		* Overwrite the current status
    		*/
	    	Map<String,AttributeValue> item = new HashMap<String,AttributeValue>();

	        String tweetID = "" + statusObj.get("TweetID");
	        String time = (String)statusObj.get("time");
	        String latitude = "" + statusObj.get("latitude");
	        String longitude = "" + statusObj.get("longitude");
	        String userID = ""+statusObj.get("userID");
	        String screenName = (String)statusObj.get("screenName");
	        String text = (String)statusObj.get("text");
	        String status = (String)statusObj.get("status");

	        String docSentiment = (String) statusObj.get("docSentiment");
	        JSONParser jsonParser = new JSONParser();
	        JSONObject sentimentJsonObject = (JSONObject)jsonParser.parse(docSentiment);
	        String score = "" + sentimentJsonObject.get("score");
	        String type = (String)sentimentJsonObject.get("type");

	        System.out.println(score);

	        item.put( "TweetID" , new AttributeValue().withS( tweetID ) );
	        item.put( "time"  , new AttributeValue().withS( time ) );
	        item.put( "latitude" ,  new AttributeValue().withN( latitude ) );
	        item.put( "longitude" , new AttributeValue().withN( longitude ) );
	        item.put( "userID" ,  new AttributeValue().withN( userID ) );
	        item.put( "screenName" , new AttributeValue().withS( screenName ) );
	        item.put( "text" , new AttributeValue().withS( text ) );
	        item.put( "status" , new AttributeValue().withS( status ));
	        item.put( "score" , new AttributeValue().withN( score ) );
	        item.put( "type" , new AttributeValue().withS( type ));

	   		PutItemRequest putItemRequest = new PutItemRequest()
    					    .withTableName(tableName)
    					    .withItem(item);

    		PutItemResult putItemResult = dynamoDBClient.putItem(putItemRequest);

    		/*
    		* Check to see if the update happened
    		*/
			Map.Entry<String,AttributeValue> hashKey;
			hashKey = new SimpleImmutableEntry<String,AttributeValue>("TweetID",new AttributeValue().withS( tweetID ));
			Map.Entry<String,AttributeValue> rangeKey;
			rangeKey = new SimpleImmutableEntry<String,AttributeValue>("time",new AttributeValue().withS( time ));
	   		GetItemRequest getItemRequest = new GetItemRequest()
                    .withTableName(tableName)
                    .withKey(hashKey,rangeKey)
                    .withConsistentRead(true);

            GetItemResult getItemResult = dynamoDBClient.getItem( getItemRequest );
            Map<String,AttributeValue> itemMap = getItemResult.getItem();

            System.out.println( );
            //System.out.println(statusObj);
            for( String attribute : itemMap.keySet() ){
                AttributeValue attrVal = itemMap.get(attribute);
                System.out.println( attribute + " " + attrVal);
            }
	    	

    	}catch(AmazonServiceException ase){
			System.out.println("Caught AmazonServiceException ");
            ase.printStackTrace();
            System.exit(-1);
        }catch( ParseException pE){
			System.out.println("Caught JSON ParseException " + pE);
			pE.printStackTrace();
			System.exit(-1);
		}
    	
    }
}