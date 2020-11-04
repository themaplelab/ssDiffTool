package testexamples.methodadditionmonomorphic;

public class AdditionMethodMonomorphicTest {

    public int field;
    
    public int returnSamePlusSeven(int x){
	field = 7;
	int temp = field;
	//int temp = newMethod(x);
	//thirdNewMethod(); //test for ref replacement, not result
	return temp;
    }

    public void testPrinter(){
	System.out.println("Hello World!");
    }

    /*
    public int newMethod(int y){
	testPrinter();
	secondNewMethod(); //test for ref replacement, not result
	return field + y;
    }

    public void secondNewMethod(){
	System.out.println("This is second new method!");
    }

    public static void thirdNewMethod(){
	System.out.println("This is third new method!");
    }
    */
}
