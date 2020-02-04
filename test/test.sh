####################
# runs the agent
# agent requires following args, comma separated and in specified order:
# $redefdir,$cp,$runRename 
#
####################

if [ -z ${1+x} ]; then

	echo "No aruments provided. Run this script as: ./test.sh <path to rt.jar>:<path to jce.jar>"

else
	
pwwd=$(pwd)
projectdir=${pwwd%test}
latestssbuild=$(find ${projectdir}target/ -name "ssDiffTool*.jar")

echo "-----------------------"
echo "SETUP:"
echo "-----------------------"
echo "Using this build of ssDiffTool: "$latestssbuild
echo "Using this as project dir: "$projectdir
echo "-----------------------"

declare -a originals=("originalForVirt4")
declare -a redefinitions=("methodAddVirtual4")
declare -a args=("hellotop hello childhello childhellotwo childhellotwochild childhellotop")

mkdir testoutputs

javac -cp .:$latestssbuild TransformTestAgent.java
jar cmf manifest.txt transformtestagent.jar TransformTestAgent.class

for testset in "${!originals[@]}"
do

	runRename="true"
	rm adapterOutput/*
	
	for listarg in "${args[@]}"
	do
		for arg in $listarg
		do
			echo "-----------------------"
			echo "Running test setup, original: ${originals[testset]}, redefinition: ${redefinitions[testset]}, with arg: $arg"
			
			redefdir=${projectdir}test/tests/${redefinitions[testset]}/
			originaldir=${projectdir}test/tests/${originals[testset]}/
			
			cp="$originaldir:.:$latestssbuild:$1:${projectdir}test/adapterOutput/:"${redefdir}
			
			java -cp $cp -javaagent:transformtestagent.jar=$originaldir,$redefdir,$cp,$runRename TestRunner $arg  &> testoutputs/${originals[testset]}$arg.out
			runRename="false" #if there are extra runs of this setup, save a few rename phases

			#do the test checking, using a predetermined expected output
			if grep -q "I am the redefined test call" testoutputs/${originals[testset]}$arg.out; then
				lastLine=$(tail -n 1 testoutputs/${originals[testset]}$arg.out)
				if echo $lastLine | grep -q -f  testExpecteds/${originals[testset]}$arg.out; then
					echo "TEST PASSED"
				else
					echo "TEST FAILED"
				fi
			else
				echo "TEST FAILED"
			fi
			echo "-----------------------"
		done
	done
done
fi
