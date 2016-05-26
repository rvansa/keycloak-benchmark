#!/bin/bash

SERVER=$1
KEYCLOAK_DIST=$2
SERVER_DIR=/tmp/$SERVER
export JBOSS_HOME=$SERVER_DIR

# Unpack server
rm -rf $SERVER_DIR
mkdir $SERVER_DIR
if [[ $KEYCLOAK_DIST == *.tar.gz ]]; then
	tar -xzf /tmp/$KEYCLOAK_DIST -C $SERVER_DIR --strip-components=1
elif [[ $KEYCLOAK_DIST == *.zip ]]; then
	unzip -n /tmp/$KEYCLOAK_DIST -d $SERVER_DIR && mv $SERVER_DIR/`ls $SERVER_DIR`/* $SERVER_DIR
else
	echo "ERROR Cannot unpack server distribution!";
	exit 1;
fi

# Add postgres driver module
POSTGRES_CACHE=/tmp/postgresql-9.4.1207.jar
if [ ! -e $POSTGRES_CACHE ]; then
    wget https://jdbc.postgresql.org/download/postgresql-9.4.1207.jar -P /tmp
fi
POSTGRES_DIR=$SERVER_DIR/modules/system/layers/base/org/postgresql/main/
mkdir -p $POSTGRES_DIR
cp $POSTGRES_CACHE $POSTGRES_DIR

cat > $POSTGRES_DIR/module.xml <<'END_OF_MODULE'
<?xml version="1.0" encoding="UTF-8"?>
<module xmlns="urn:jboss:module:1.3" name="org.postgresql">
    <resources>
        <resource-root path="postgresql-9.4.1207.jar"/>
    </resources>
    <dependencies>
        <module name="javax.api"/>
        <module name="javax.transaction.api"/>
    </dependencies>
</module>
END_OF_MODULE

# Add MySQL driver module
MYSQL_CACHE=/tmp/mysql-connector-java-5.1.39-bin.jar
if [ ! -e $MYSQL_CACHE ]; then
   wget http://dev.mysql.com/get/Downloads/Connector-J/mysql-connector-java-5.1.39.zip -P /tmp
   unzip /tmp/mysql-connector-java-5.1.39.zip -d /tmp
   cp /tmp/mysql-connector-java-5.1.39/mysql-connector-java-5.1.39-bin.jar $MYSQL_CACHE
fi

MYSQL_DIR=$SERVER_DIR/modules/system/layers/base/com/mysql/main/
mkdir -p $MYSQL_DIR
cp $MYSQL_CACHE $MYSQL_DIR

cat > $MYSQL_DIR/module.xml <<'END_OF_MODULE'
<?xml version="1.0" encoding="UTF-8"?>
<module xmlns="urn:jboss:module:1.3" name="com.mysql">
    <resources>
        <resource-root path="mysql-connector-java-5.1.39-bin.jar"/>
    </resources>
    <dependencies>
        <module name="javax.api"/>
        <module name="javax.transaction.api"/>
    </dependencies>
</module>
END_OF_MODULE

# Add admin user; this will result in failure to load it in all but one servers
$SERVER_DIR/bin/add-user.sh -u admin -p admin

# Copy contents of standalone/configuration  to server configuration dir
CFG_DIR=$SERVER_DIR/domain/servers/$SERVER/configuration
mkdir -p $CFG_DIR
cp -r $SERVER_DIR/standalone/configuration/* $CFG_DIR

# Overwrite host configuration
sed 's/server-name-to-be-replaced/'$SERVER'/' < /tmp/host.xml > $SERVER_DIR/domain/configuration/host.xml
