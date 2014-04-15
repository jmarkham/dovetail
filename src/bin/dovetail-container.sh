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

##
# Creates a Dovetail container.
##

##
#  Change permissions on JBoss home
##

export JBOSS_HOME=$(find $JBOSS_HOME_PARENT_LINK/ -maxdepth 1 -type d -name jboss*)

echo "Changing permissions on $JBOSS_HOME" 
chmod -R 777 $JBOSS_HOME

##
#  Configure JBoss AS
##

echo "Configuring JBoss..." 
$JAVA_HOME/bin/java -cp $(hadoop classpath):$DOVETAIL_APP org.hortonworks.dovetail.util.AppServerConfiguration

##
#  Start JBoss AS
##

echo "Starting JBoss.." 
$JBOSS_HOME/bin/domain.sh -Djboss.bind.address=$JBOSS_HOST -Djboss.bind.address.management=$JBOSS_HOST -Djboss.bind.address.unsecure=$JBOSS_HOST