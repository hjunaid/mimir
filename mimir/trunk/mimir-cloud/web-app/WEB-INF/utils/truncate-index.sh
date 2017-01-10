#!/usr/bin/env bash
#
# Simple script to run the index repair tool - you may need to edit the command
# line to increase or decrease the -Xmx for your system.
#
# IT IS HIGHLY RECOMMENDED TO BACK UP YOUR INDEX BEFORE ATTEMPTING TO REPAIR IT!
# The repair process is potentially destructive, particularly if it crashes part
# way through (e.g. running out of memory).  If this happens you will have to
# restore the index from your backup, fix the problem (e.g. a higher -Xmx) and
# try again.
#
# Usage:
#
#   bash truncate-index.sh [-p /path/to/extra/plugin ...] /path/to/index-NNNNN.mimir
#
# The script will automatically include the plugins that are bundled inside
# this WAR file (in WEB-INF/mimir-plugins), if your index refers to classes
# from any other mimir plugins that you have referenced via local configuration
# then you must load those plugins yourself with appropriate -p options.
#
# The final option on the command line should be the path to the top-level
# directory of the Mimir index you want to repair (i.e. the directory that
# contains config.xml, mimir-collection-*.zip and all the token-N and mention-N
# subdirectories).
#

DIR="`dirname $0`"

if [ -z "$JAVA_HOME" ]; then
  echo "JAVA_HOME not set"
  exit 1
fi

# enumerate all the mimir-plugins
plugins=()
for plug in "$DIR"/../mimir-plugins/* ; do
  plugins=( "${plugins[@]}" -p "$plug" )
done

"$JAVA_HOME/bin/java" -Xmx2G -classpath "$DIR:$DIR/../lib/*" gate.mimir.util.TruncateIndex "${plugins[@]}" "$@"
