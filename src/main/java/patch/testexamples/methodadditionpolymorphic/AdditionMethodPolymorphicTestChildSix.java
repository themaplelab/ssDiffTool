package patch.testexamples.methodadditionpolymorphic;

/*
 * grand-child class which relies on:                    
 * 1) parent method in original version
 * 2) overriding method in patch version
 */

public class AdditionMethodPolymorphicTestChildSix extends AdditionMethodPolymorphicTestChildTwo {

    public int emitMethod(){
	return 6;
    }
    
}
