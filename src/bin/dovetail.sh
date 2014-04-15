#!/usr/bin/env bash

# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.


# Runs a Dovetail command.
#
# Environment Variables
#
#   DOVETAIL_CONF_DIR  Alternate conf dir. Default is ${HADOOP_YARN_HOME}/conf.
#   DOVETAIL_LOG_DIR   Where log files are stored.  PWD by default.
##

DOVETAIL_USER=dovetail
DOVETAIL_GROUP=hadoop
DOVETAIL_AM_HDFS_DIR=/apps/dovetail/am
DOVETAIL_DIST_HDFS_DIR=/apps/dovetail/dist
DOVETAIL_CONF_HDFS_DIR=/apps/dovetail/conf
DOVETAIL_BIN_HDFS_DIR=/apps/dovetail/bin
DOVETAIL_HOME="$( cd "$( dirname "${BASH_SOURCE[0]}" )"./ && pwd )"
DOVETAIL_LOG_DIR=/var/log/dovetail
DOVETAIL_CONF_DIR=$DOVETAIL_HOME/conf
DOVETAIL_LIB_DIR=$DOVETAIL_HOME/lib
JAVA_HOME=/usr/jdk64/jdk1.7.0_45

CMD_OPTIONS=$(getopt -n "$0"  -o isS --long "init,start,stop"  -- "$@")

if [ $? -ne 0 ];
then
  exit 1
fi
eval set -- "$CMD_OPTIONS"

start()
{
	echo "Starting Dovetail..."
	
	CLASSPATH=$(hadoop classpath):"$DOVETAIL_LIB_DIR/*:$DOVETAIL_CONF_DIR"
    JAVA_OPTS="-Ddovetail.log.dir=$DOVETAIL_LOG_DIR"
    JAVA_OPTS="$JAVA_OPTS -Ddovetail.home=$DOVETAIL_HOME"
    JAVA_OPTS="$JAVA_OPTS -Ddovetail.java.home=$JAVA_HOME"
    JAVA="$JAVA_HOME/bin/java"

    su - $DOVETAIL_USER -c "$JAVA $JAVA_OPTS -cp $CLASSPATH org.hortonworks.dovetail.client.Client" "$@"
}

stop()
{
	echo "Stopping Dovetail..."
	APP_ID=$(yarn application -list | awk '/Dovetail/ {print $1}')
    yarn application -kill $APP_ID
}

init()
{
	# Create the group if it doesn't exist
	/bin/id -g $DOVETAIL_GROUP 2>/dev/null
	if [ $? -ne 0 ]
	then
    	echo "Group $DOVETAIL_GROUP found"
	else
    	echo "Creating group $DOVETAIL_GROUP"
    	groupadd $DOVETAIL_GROUP
	fi

	# Create the user if it doesn't exist
	/bin/id $DOVETAIL_USER 2>/dev/null
	if [ $? -ne 0 ]
	then
    	echo "User $DOVETAIL_USER found"
	else
    	echo "Creating user $DOVETAIL_USER"
    	useradd -g $DOVETAIL_GROUP $DOVETAIL_USER
    fi
    
	#Create HDFS directories
	echo "Creating Dovetail Application Master directory..."
	su - hdfs -c "hdfs dfs -mkdir -p $DOVETAIL_AM_HDFS_DIR"
	su - hdfs -c "hdfs dfs -chown $DOVETAIL_USER:$DOVETAIL_GROUP $DOVETAIL_AM_HDFS_DIR"
	su - hdfs -c "hdfs dfs -chmod 755 $DOVETAIL_AM_HDFS_DIR"

	echo "Creating JBoss AS distribution directory..."
	su - hdfs -c "hdfs dfs -mkdir -p $DOVETAIL_DIST_HDFS_DIR"
	su - hdfs -c "hdfs dfs -chown $DOVETAIL_USER:$DOVETAIL_GROUP $DOVETAIL_DIST_HDFS_DIR"
	su - hdfs -c "hdfs dfs -chmod 755 $DOVETAIL_DIST_HDFS_DIR"

	echo "Creating Dovetail configuration directory..."
	su - hdfs -c "hdfs dfs -mkdir -p $DOVETAIL_CONF_HDFS_DIR"
	su - hdfs -c "hdfs dfs -chown $DOVETAIL_USER:$DOVETAIL_GROUP $DOVETAIL_CONF_HDFS_DIR"
	su - hdfs -c "hdfs dfs -chmod 755 $DOVETAIL_CONF_HDFS_DIR"
	
	echo "Creating Dovetail bin directory..."
	su - hdfs -c "hdfs dfs -mkdir -p $DOVETAIL_BIN_HDFS_DIR"
	su - hdfs -c "hdfs dfs -chown $DOVETAIL_USER:$DOVETAIL_GROUP $DOVETAIL_BIN_HDFS_DIR"
	su - hdfs -c "hdfs dfs -chmod 755 $DOVETAIL_BIN_HDFS_DIR"
	
	#Create Dovetail log dir
	echo "Creating Dovetail log directory..."
	mkdir -p -m 755 $DOVETAIL_LOG_DIR
	chown $DOVETAIL_USER:$DOVETAIL_GROUP $DOVETAIL_LOG_DIR
}
    
help()
{
cat << EOF
dovetail.sh 
 
This script handles Dovetail initialization, start, and stop. 
 
USAGE:  dovetail.sh [options]
 
OPTIONS:
   -i, --init      Prompt for fully qualified domain names (FQDN) of the NameNode,
                   Secondary NameNode, DataNodes, ResourceManager, NodeManagers,
                   MapReduce Job History Server, and YARN Proxy server.  Values
                   entered are stored in files in the same directory as this command. 
                          
   -s, --start     Use files with fully qualified domain names (FQDN), new-line
                   separated.  Place files in the same directory as this script. 
                   Services and file name are as follows:
                   NameNode = nn_host
                          
   -S, --stop      Use files with fully qualified domain names (FQDN), new-line
                   separated.  Place files in the same directory as this script. 
                   Services and file name are as follows:
                   NameNode = nn_host                       
                          
   -h, --help      Show this message.
   
EXAMPLES: 
   Initialize : 
     dovetail.sh -i
     dovetail.sh --init
   
   Start Dovetail:
     dovetail.sh -s
     dovetail --start
             
EOF
}

while true;
do
  case "$1" in

    -h|--help)
      help
      exit 0
      ;;
    -i|--init)
      init
      shift
      ;;
    -s|--start)
      start
      shift
      ;;
    -S|--stop)
      stop
      shift
      ;;
    --)
      shift
      break
      ;;
  esac
done