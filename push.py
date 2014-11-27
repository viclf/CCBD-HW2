import os
import sys
import subprocess
from termcolor import colored


#push git new commits every 5 minutes
subp51 = subprocess.call(['git add .'],shell=True)
subp52 = subprocess.call(['git commit -m "update app"'],shell=True)
subp53 = subprocess.call(['git aws.push'],shell=True)
print(colored("Git pushed\n","green"))