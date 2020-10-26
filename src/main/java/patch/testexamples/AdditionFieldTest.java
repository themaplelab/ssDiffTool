package patch.testexamples;

public class AdditionFieldTest {

    public int newField;
    public static int secondNewField = 1;
    
    public int returnSame(int x){
	int local = x;
	newField = local;
	return newField;
    }

    public int returnAValue(){
	return secondNewField;
    }

}
