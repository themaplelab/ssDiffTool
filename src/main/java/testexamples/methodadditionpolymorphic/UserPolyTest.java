package testexamples.methodadditionpolymorphic;

public class UserPolyTest {

    public int use(int decision){
	AdditionMethodPolymorphicTestParent p;
	if(decision == 1){
	    p = new AdditionMethodPolymorphicTestChildOne();
	} else if(decision == 2) {
	    p =	new AdditionMethodPolymorphicTestChildTwo();
	} else if(decision == 3) {
	    p = new AdditionMethodPolymorphicTestChildThree();
	} else if(decision == 4) {
	    p = new AdditionMethodPolymorphicTestChildFour();
	} else if(decision == 5) {
	    p = new AdditionMethodPolymorphicTestChildFive();
	} else if(decision == 6) {
	    p = new AdditionMethodPolymorphicTestChildSix();
	} else {
	    p = new AdditionMethodPolymorphicTestParent();
	}
	return p.emitMethod();
    }

}
