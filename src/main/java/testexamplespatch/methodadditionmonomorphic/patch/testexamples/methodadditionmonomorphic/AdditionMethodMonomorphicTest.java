package patch.testexamples.methodadditionmonomorphic;

/*
 * Test scenarios for new methods:
 * 
 * (old == pre-existing)
 *
 * 1) new instance call in old method
 * 2) new static call in old method
 * 3) old instance method call in new method 
 * 4) new instance method call in new method
 * 5) old field access in new method
 */

public class AdditionMethodMonomorphicTest {

    public int field;
    
    public int returnSamePlusSeven(int x){
	field = 7;
	int temp = newMethod(x); //1
	thirdNewMethod(); //2
	return temp;
    }

    public void testPrinter(){
	System.out.println("Hello World!");
    }

    public int newMethod(int y){
	testPrinter(); //3
	secondNewMethod(); //4
	return field + y; //5
    }

    public void secondNewMethod(){
	System.out.println("This is second new method!");
    }

    public static void thirdNewMethod(){
	System.out.println("This is third new method!");
    }
}
