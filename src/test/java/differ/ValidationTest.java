import java.io.File;
import java.util.Arrays;
import org.junit.Test;
import soot.G;
import org.junit.Assert;

public class ValidationTest {

	@Test
	public void testRunRename() throws Throwable {
	    String mainClass = "testexamples.NoChangeTest";
	    String[] classes = {mainClass};	    

	    TestSetup.makeListFiles(classes);	    
	    String[] differArgs =  TestSetup.setup(mainClass);
	    TestSetup.runAdapter(Arrays.asList(mainClass), differArgs);
	    
	    Class<?> clazz = TestSetup.validateClassFile(mainClass);
	    java.lang.reflect.Method methodHandle = clazz.getMethod("calcSquare", int.class);
	    Assert.assertEquals(16, methodHandle.invoke(null, 4));  
	    TestSetup.testSetupRefresh();
	}

    @Test
    public void testRunFieldAddition() throws Throwable {
	String mainClass = "testexamples.AdditionFieldTest";
	String[] classes = {mainClass};

	TestSetup.makeListFiles(classes);
	String[] differArgs =  TestSetup.setup(mainClass);
	TestSetup.runAdapter(Arrays.asList(mainClass), differArgs);
	
	Class<?> clazz = TestSetup.validateClassFile(mainClass);

	Object obj = clazz.newInstance(); 
	
	java.lang.reflect.Method methodHandleOne = clazz.getMethod("returnSame", int.class);
	Assert.assertNotNull(methodHandleOne);
	Assert.assertEquals(3, methodHandleOne.invoke(obj, 3));

	java.lang.reflect.Method methodHandleTwo = clazz.getMethod("returnAValue");
	Assert.assertNotNull(methodHandleTwo);
        Assert.assertEquals(1, methodHandleTwo.invoke(obj));
	TestSetup.testSetupRefresh();
    }

    @Test
     public void testRunMethodAdditionMonomorphic() throws Throwable {
	String mainClass = "testexamples.AdditionMethodMonomorphicTest";
	String[] classes = {mainClass};

        TestSetup.makeListFiles(classes);
        String[] differArgs =  TestSetup.setup(mainClass);
        TestSetup.runAdapter(Arrays.asList(mainClass), differArgs);

        Class<?> clazz = TestSetup.validateClassFile(mainClass);

        Object obj = clazz.newInstance();

        java.lang.reflect.Method methodHandleOne = clazz.getMethod("returnSamePlusSeven", int.class);
        Assert.assertNotNull(methodHandleOne);
        Assert.assertEquals(8, methodHandleOne.invoke(obj, 1));
    }
}
