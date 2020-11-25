###################
# This starts up the application JVM
# must start up after the CogniServer portion
#
################### 

#<misuse#> <classname>
repo="cryptoapi-bench/build/classes/java/main/"
i=$1
class=$2

cd /root/cryptoapi-bench
echo "TESTING: $repo/$i"

touch /root/basicBmkResults/$i/TEST.txt
touch /root/basicBmkResults/$i/REPORT.txt

#this is the bit that actually runs the application, the rest is output redirection and parsing
testCommand="gradle test -Darg1=$class --info --rerun-tasks"
eval "${testCommand}" &> /root/basicBmkResults/$i/TEST.txt

    echo "Running with: ${testCommand}" > /root/basicBmkResults/$i/REPORT.txt
    echo "Results:" >> /root/basicBmkResults/$i/REPORT.txt
    echo "---------------------------------------" >> /root/basicBmkResults/$i/REPORT.txt
	find /root/cryptoapi-bench/build/test-results/test/ -name "*.xml" | xargs -I '{}' mv {} /root/basicBmkResults/$i/
    grep "tests=" /root/basicBmkResults/$i/TEST-randoopTest.RegressionTest*.xml >> /root/basicBmkResults/$i/REPORT.txt

	grep "Located the class in the cache: $class" /root/basicBmkResults/$i/ALL.txt >>  /root/basicBmkResults/$i/REPORT.txt

	echo "................................." >> /root/basicBmkResults/$i/REPORT.txt
	
	echo "................................." >>	/root/basicBmkResults/$i/REPORT.txt
	
	if grep -q "REDEFINITION PASSED" /root/basicBmkResults/$i/TEST.txt ; then

		echo "REDEFINITION PASSED" >> /root/basicBmkResults/$i/REPORT.txt

	else
		echo "REDEFINITION FAILED" >> /root/basicBmkResults/$i/REPORT.txt
		
	fi

	echo "---------------------------------------" >> /root/basicBmkResults/$i/REPORT.txt
	echo "................................."
	cat /root/basicBmkResults/$i/REPORT.txt
	echo "................................."
	echo "................................."
	#depending on how long this has run for, this might not include the most relevant runs of CogniCrypt that it was trying to scrape for
	awk '/======================= CogniCrypt Summary ==========================$/,/=====================================================================$/' /root/basicBmkResults/$i/ALL.txt
