# ssDiffTool (Soot Semantic DiffTool)
  * a tool for determining the differences between two versions of a class
  * differences are output in terms of Java standard nonpermissible types of redefinition changes, from the JVMTI/Java Agent mechanism
  * this project depends on [Soot](https://github.com/Sable/soot)!


## Standards:
  * currently follows [Instrumentation API](https://docs.oracle.com/javase/8/docs/api/java/lang/instrument/Instrumentation.html#redefineClasses-java.lang.instrument.ClassDefinition...-) constraints from `redefineClasses` method in JavaSe 8


## How to build:
  * `mvn compile`


## How to run:
  * a hackish way for now until packaging is setup:

```
java -Xmx2g -cp $depdir:/root/semantic-differ/target/classes:target/sootclasses-trunk-jar-with-dependencies.jar differ.SemanticDiffer -cp .:/root/difftests/original:$diffdir:/root/openj9-openjdk-jdk8/build/linux-x86_64-normal-server-release/images/j2sdk-image/jre/lib/rt.jar:/root/openj9-openjdk-jdk8/build/linux-x86_64-normal-server-release/images/j2sdk-image/jre/lib/jce.jar -w -firstDest diffresults -altDest secondresults -f c -redefcp $diffdir Example
```

  * where:
     * `/root/difftests/original` contains the original class to analyse
	 * diffdir is the directory of the redefined version of the original class
	 * depdir is some path to the `commons-cli` library, which is currently not included, as it is not packaged
	 * Example is any class, as a placeholder to get Soot up and running in a way that it is familiar with
