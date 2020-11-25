#can find all specific misuse labels and classes here:
#https://github.com/themaplelab/cryptoapi-bench/tree/master/patch
declare -a repos=(
"misuse1"
)

declare -a  classes=(
"org.cryptoapi.bench.staticsalts.StaticSaltsABHCase1"
)

#originally meant to cycle all, but for experiments had to verify everything one by one
for index in "${!repos[@]}"
do
echo "THIS JUST UP: ${repos[index]}"
echo "THIS class ${classes[index]}"
	echo $(date)
echo "############################"

i=${repos[index]}

rm /root/basicBmkResults/$i/TEST.txt /root/basicBmkResults/$i/REPORT.txt /root/basicBmkResults/$i/ALL.txt /root/basicBmkResults/$i/*.xml /root/basicBmkResults/$i/adapterOutputs/*
mkdir /root/basicBmkResults /root/basicBmkResults/$i /root/basicBmkResults/$i/adapterOutputs

#move the tests around
find /root/cryptoapi-bench/src/test/java/randoopTest/ -name "RegressionTest*" | xargs rm
find /root/cryptoapi-bench/patch/$i/randoopTest/ -name "RegressionTest*" | xargs cp -t /root/cryptoapi-bench/src/test/java/randoopTest/

#start up CogniServer
cd /root/CryptoAnalysis/
./runCogniServerBasic.sh $i &

#startup application JVM
cd /root/
sleep 40
./runHotFixBasic.sh $i ${classes[index]}
echo "############################"

#move patch adapter outputs outta there
sleep 75 #last cogni run can happen quite some time later...
echo "MOVING: "
find /root/CryptoAnalysis/adapterOutput -name "*.class"
echo "INTO: "
echo "/root/basicBmkResults/$i/adapterOutputs"
#designed to auto move the outputs to another folder for record keeping
#BUT this can sometimes run before the tests... which causes failures obviously as the class is needed in a certain cp for the tests...
#find /root/CryptoAnalysis/adapterOutput -name "*.class" | xargs -I '{}' mv {} /root/basicBmkResults/$i/adapterOutputs

done
