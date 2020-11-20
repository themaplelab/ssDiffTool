####################
# makes the agent
####################

javac -Xdiags:verbose Agent.java
if [ -f Agent.class ]; then
	jar cmf manifest.txt agent.jar *.class
fi
 
