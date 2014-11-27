import boto
from boto.dynamodb2 import connect_to_region
from boto.dynamodb2.items import Item
from boto.dynamodb2.table import Table
import time
from datetime import datetime, timedelta

class DynamoDB:

    def __init__(self, keyid, secretkey,region,name,endpoint=None, port=None):
        self.tweetsTable = None
        self.db = None
        
        if port is None:
            port = 8000
        
        self.db = connect_to_region(region, aws_access_key_id=keyid, aws_secret_access_key=secretkey)
        self.tweetsTable = Table(name,connection=self.db)


    def getTweetsForView(self):
        tweets=[]
        # keep track of last hour tweet to display on the map (UTC time)
        utc_time=datetime.utcnow()
        utc_time=utc_time.strftime("%a %b %d %H")
        result = self.tweetsTable.scan(time__beginswith=utc_time)#,limit=0)
        #timenow = time.time()
        for x in result:
            #timetweet=self.set_date(x['time'])
            if x['latitude']!=None: #and timenow+14400-timetweet<2000:
                tweets.append([float(str(x['latitude'])),float(str(x['longitude'])),x['text']])
        return tweets


    def getTweetsBySearch(self,search):
        tweets=[]
        search='#'+search
        utc_time=datetime.utcnow()
        utc_time=utc_time.strftime("%a %b %d")
        utc_time_y=datetime.utcnow() - timedelta(days=1)
        utc_time_y=utc_time_y.strftime("%a %b %d")
        result1 = self.tweetsTable.scan(time__beginswith=utc_time)#,limit=20000)
        result2 = self.tweetsTable.scan(time__beginswith=utc_time_y)#, limit=20000)
        for x,y in zip(result1,result2):
            if x['latitude']!=None and y['latitude']!=None:
                if search in x['text']:
                    tweets.append([float(str(x['latitude'])),float(str(x['longitude'])),x['text']])
                if search in y['text']:
                    tweets.append([float(str(y['latitude'])),float(str(y['longitude'])),y['text']])
        return tweets


    def getTweetsWithSentiment(self):
        tweets=[]
        result = self.tweetsTable.scan(status__beginswith="OK")
        for x in result:
            if x['latitude']!=None:
                if x['type']=="neutral":
                    tweets.append([float(str(x['latitude'])),float(str(x['longitude'])),0])
                if x['type']=="positive":
                    tweets.append([float(str(x['latitude'])),float(str(x['longitude'])),float(str(x['score']))])
                if x['type']=="negative":
                    tweets.append([float(str(x['latitude'])),float(str(x['longitude'])),float(str(x['score']))])
        return tweets


    def set_date(self, date_str):
        time_struct = time.strptime(date_str, "%a %b %d %H:%M:%S UTC %Y")
        date = time.mktime(time_struct)
        return date





