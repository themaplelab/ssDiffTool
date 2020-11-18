package patch.testexamples.methodremoval;

/*
 * child class which relies on:                                                                                                  
 * 1) overriding method in original version and patch version
 */

public class AdditionMethodPolymorphicTestChildTwo extends AdditionMethodPolymorphicTestParent {

    public int emitMethod(){
	return 2;
    }
    
}
