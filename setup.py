#!/usr/bin/env python
import os
import sys
import subprocess
from termcolor import colored


#default param
regionName = 'us-east-1'

#check if project exists and init
if not os.path.exists("./.git/"):
    print(colored("No git folder","yellow"))
    subp1 = subprocess.check_output(['git','init'])
    print(colored("git repository created\n","green"))

#elif not os.path.exists("./.elasticbeanstalk/"):

else:
    subp2 = subprocess.call(['export PATH=$PATH:/Users/Vico/AWS-ElasticBeanstalk-CLI-2.6.3/eb/linux/python2.7/'],shell=True)
    subp3 = subprocess.call(['eb init -I ... -S ... -a CCBD-HW2 -e CCBD-HW2-env -t 1 -s 64bit Amazon Linux 2014.03 v1.0.9 running Python --force --region us-east-1'],shell=True)
    print(colored("elasticbeanstalk initialized\n","green"))


#create application
subp3 = subprocess.call(['eb start'],shell=True)
print(colored("elasticbeanstalk started\n","green"))
