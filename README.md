Dovetail:  Hadoop 2 and JBoss AS

=========

Build:

mvn clean package assembly:single

Run on a machine with a Hadoop 2 client:

Initialize...
$DOVETAIL_HOME/bin/dovetail.sh -i

Start...
$DOVETAIL_HOME/bin/dovetail.sh -s

Stop...
$DOVETAIL_HOME/bin/dovetail.sh -S