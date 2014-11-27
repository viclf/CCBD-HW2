Cloud Computing HW2


Working on this assignment:
vf2221 victor Ferrand
pwn2107 Peter Wakahiu Njenga


This is a tweet map using google map API, Twitter Public stream API, and the Alchemy API for the sentiment analysis
The heat map can be seen with the screenshot heatmap.png



Tweet Stream
In this application, we build a twitter sentiment analyzer based on the Alchemy API. The application starts by receiving public tweets from the twitter streaming API. We filter those tweets with Geo-info and put them into DynamoDB. We then send these twitters into a SQS queue for sentiment analysis via the Alchemy  HTTP API. A thread pool polls tweets at the head of the SQS queue and performs sentiment analysis. Individual threads determine if tweets are likely to yield meaningful sentiment analysis by performing pre-processing such as determining their language. After sentiment analysis the tweets are sent over to the front-end EBS server via SNS. 
Source: Twitstreamer folder



Database
We implemented a DynamoDB database to store the tweets with the relevant values we needed
Our database is about 50MB large for now (4 days of stream) and still growing!
Normally should approximate  200MB by next week.



App
This application is live as it pushes new tweets via web sockets from the server to the renderer in the page.
It displays only the tweets from the last hour (otherwise the map is not readable !)

The application was built using Flask framework on the server side.
There are two scripts setup.py and push.py. They create programmatically a new elastic bean stalk  environment/application(with the option provided) and launch it, and also push it (git) for updates. The program is calling command line functions to launch it with the AWS elastic beanStalk CLI.
However the EBS platforms don’t allow web sockets to communicate because of the load balancer policies. I did not find a way to rewrite the http proxy rules to accept web sockets and open a connection. Therefore the application is running on an ec2 instance without a load balancer. The instance was configured to be used as a server.

The search function is looking for tweets by hashtags and display them on the map (only tweets from the past 24h).
Each tweet is readable on the map by clicking on it.
To go back to the main map, just enter an empty search.

The map is kind of a density map as the tweet's markers have been made transparents. The superposition of those  gives a darker color which provides an idea of the tweet's density !

The heat map represents the sentiment from red, negative to green, positive. As the alchemy API only allow us to make 1000 call per day, we could not have the heat map streaming live. Therefore the heat map renders all the tweets that have a not none sentiment value regardless of their time stamp. This restriction prevented us from doing it live, but the structure of the application is made to display a live heat map (pushes tweets with sentiment value in the stream notifications).

Link to application: http://ec2-54-174-110-195.compute-1.amazonaws.com:5000/



Github source code:
https://github.com/viclf/CCBD-HW2
Keys have been removed from the python files for security purposes
