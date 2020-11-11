package testexamples;

public class AdditionFieldTest {

    //public int newField;
    //public static int secondNewField = 1;
    
    public int returnSame(int x){
	int local = x;
	//newField = local;
	//return newField;
	return local + 1;
    }

    public int returnAValue(){
	//return secondNewField;
	return 7;
    }

}
