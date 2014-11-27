import flask
import time
import json
import urllib
from dynamoDB import DynamoDB
from flask import Flask, render_template, request, session, flash, redirect, jsonify, json
from datetime import datetime
from flask import request, Response
from flask.ext.socketio import SocketIO, emit, send
from gevent import monkey


#init
application = flask.Flask(__name__)
application.debug=True
application.config['SECRET_KEY'] = 'secret!'
socketio = SocketIO(application)
monkey.patch_all()


#Connect the Dynamo DB and get the Tweets Table
DB = DynamoDB('...','...','us-east-1','TweetsTable3')



#application
@application.route('/')
def displayTweets():
    tweets = DB.getTweetsForView()
    return render_template('index.html',tweets=json.dumps(tweets))


#search
@application.route('/search',methods=['POST','GET'])
def searchTweets():
    search = request.form['searchTweets']
    if search!='':
        global tweets
        tweets = DB.getTweetsBySearch(search)
    return render_template('index.html',tweets=json.dumps(tweets))


#Notification
@application.route('/stream',methods = ['POST'])
def getTweetNotification():
    content = json.loads(request.data)
    if content['Type']=="SubscriptionConfirmation":
        url = content['SubscribeURL']
        return urllib.urlopen(url)
    else:
        jsonContent=json.loads(content['Message'])
        tweet=[[ jsonContent['latitude'],jsonContent['longitude'],jsonContent['text'] ]]
        handle_stream_event(tweet)
        return "tweet pushed"


#socket methods (push new tweets to render or heatMap)
@socketio.on('message',namespace='/stream')
def handle_message(message):
    print('received message: ' + message)

@socketio.on('pushtweet',namespace='/stream')
def handle_stream_event(msg):
    socketio.emit('pushtweet', msg ,namespace='/stream')

@socketio.on('message',namespace='/sentiment')
def handle_message(message):
    print('received message: ' + message)

@socketio.on('heatMap',namespace='/sentiment')
def heatMap():
    HMtweets=DB.getTweetsWithSentiment()
    socketio.emit('heatMap', HMtweets ,namespace='/sentiment')


if __name__ == '__main__':
    #application.run(host='0.0.0.0')
    socketio.run(application,host='0.0.0.0')


