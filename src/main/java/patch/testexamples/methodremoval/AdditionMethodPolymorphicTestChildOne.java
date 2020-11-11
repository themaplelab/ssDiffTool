package patch.testexamples.methodremoval;

/*
 * child class which relies on:                                                                                           
 * 1) overriding method in original version
 * 2) parent method in patch version 
 *
 */

public class AdditionMethodPolymorphicTestChildOne extends AdditionMethodPolymorphicTestParent {

    /*
    public int emitMethod(){
	return 1;
    }
    */
}
