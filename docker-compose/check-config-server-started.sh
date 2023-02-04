#!/bin/bash
# check-config-server-started.sh

apt-get update -y

yes | apt-get install curl

curlResult=$(curl -s -o /dev/null -I -w "%{http_code}" http://config-server:8888/actuator/health)

echo "result status code:" "$curlResult"

while [[ ! $curlResult == "200" ]]; do
  >&2 echo "Config server is not up yet!"
  sleep 2
  curlResult=$(curl -s -o /dev/null -I -w "%{http_code}" http://config-server:8888/actuator/health)
done

/cnb/process/web
# Here "/cnb/process/web" is default entrypoint for "spring boot application "
# For other OS and java and spring boot version it can be different command. If you don't know then simply comment out
# "entrypoint" and run "docker-compose up" command and check containers using "docker ps -a" command. In the result
# check "COMMAND" value for the desired container. It will be the default "entrypoint" for you. So replace
# "/cnb/process/web" with "COMMAND" value.

# In the shell script, we first run an update. Then we install the "curl" command line utility tool. Then we will
# simply run a curl command against config server actuator health end point. And then we will extract the http code.
# Then in a simple loop, we will check if we get correct response from the actuator health endpoint of config server.
# Otherwise, we will log to the console, sleep for two seconds and retry the Curl command. We will continue with this
# loop until we get correct response from config server.
# We want to continue launching our Spring boot application after we get "200" status for actuator health for
# "config-server", Adding "/cnb/process/web" will do it for as it is the original entrypoint for spring-boot
# applications.

# As a final thing, we need to change the permission for the new shell script file. As it should be runnable file. We
# can do it using following commands :
#############################################################
#   cd docker-compose
#   chmod +x check-config-server-started.sh
#############################################################


# In the services.yml file, we will override entry point for the Twitter to Kafka service and set this shell script
# that we created.