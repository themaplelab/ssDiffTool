for testset in tests/*/
do

	echo "Compiling (javac) : "$testset
	javac $testset/*.java
	done
