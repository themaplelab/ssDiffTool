###################
# This starts up the CogniServer
# component of hotfixer
# should be run ini the background so you can
# start up the applicatoin jvm component next
# give it a few seconds though, so this can setup
#
# sample usage:
#     ./runCogniServerBasic.sh <misusenumber> &
#
####################

currentdir=$(pwd)
#<misuse#>
branch=$1


projectdir=/root/cryptoapi-bench/
prefixcp=${projectdir}build/classes/java/main
redefdir=${projectdir}patch/$branch
patchlist=/root/allpatches.out


echo "--------------------"
echo "STARTING UP COGNISERVER"
echo "--------------------"

#beware, more hardcoded paths
ssDiff="/root/ssDiffTool/target/ssDiffTool-1.0-SNAPSHOT-jar-with-dependencies.jar"
rtjce="/root/openj9-openjdk-jdk8/build/linux-x86_64-normal-server-release/images/j2sdk-image/jre/lib/rt.jar:/root/openj9-openjdk-jdk8/build/linux-x86_64-normal-server-release/images/j2sdk-image/jre/lib/jce.jar"
cp=".:$ssDiff:$rtjce:${currentdir}/adapterOutput/"
rulesDir="/root/CryptoAnalysis/CryptoAnalysis/src/main/resources/JavaCryptographicArchitecture"
sootcp=$prefixcp:.:$ssDiff:$rtjce:${currentdir}/adapterOutput/:${redefdir}
differcp=$prefixcp:.:$ssDiff:$rtjce:${currentdir}/adapterOutput/:${redefdir}
crysl="/root/.m2/repository/de/darmstadt/tu/crossing/CrySL/de.darmstadt.tu.crossing.CrySL/2.0.0-SNAPSHOT/de.darmstadt.tu.crossing.CrySL-2.0.0-SNAPSHOT.jar"

#are all of these args absolutely necessary? its possible that some are not, and are residual from some dev phase...
java -Dcom.ibm.j9ddr.structurefile=/root/openj9-openjdk-jdk8/build/linux-x86_64-normal-server-release/vm/j9ddr.dat  -Xshareclasses:name=Guardtest -Djava.library.path=$LD_LIBRARY_PATH:/root/openj9-openjdk-jdk8/openj9/runtime/ddrext:/root/openj9-openjdk-jdk8/build/linux-x86_64-normal-server-release/jdk/classes/com/ibm/oti/shared/ -cp $prefixcp:$originaldir:/root/soot/target/sootclasses-trunk-jar-with-dependencies.jar:/root/openj9-openjdk-jdk8/build/linux-x86_64-normal-server-release/jdk/ddr/classes/com/ibm/j9ddr/vm29/structure/:$crysl:/root/CryptoAnalysis/CryptoAnalysis/build/CryptoAnalysis-2.7.1-SNAPSHOT-jar-with-dependencies.jar:/root/openj9-openjdk-jdk8/build/linux-x86_64-normal-server-release/jdk/classes/com/ibm/oti/shared/:/root/openj9-openjdk-jdk8/build/linux-x86_64-normal-server-release/images/j2sdk-image/jre/lib/ddr/j9ddr.jar:$cp:$suffixdir  crypto.TCPCryptoRunner --rulesDir=$rulesDir --patchlist=$patchlist -sootCp=$sootcp -redefcp $redefdir -differClasspath $differcp &> /root/basicBmkResults/$branch/ALL.txt

