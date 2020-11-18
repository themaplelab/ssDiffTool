package patch.testexamples.methodadditionpolymorphic;

/*
 * grand-child class which relies on:
 * 1) overriding method in patch version
 * 2) grand-parent method in original version
 * 
 */

public class AdditionMethodPolymorphicTestChildThree extends AdditionMethodPolymorphicTestChildOne {

    public int emitMethod(){
	return 3;
    }
    
}
