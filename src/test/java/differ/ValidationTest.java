import java.io.File;
import java.util.Arrays;
import org.junit.Test;
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
	    TestSetup.testSetupRefresh("testexamples/");
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
	TestSetup.testSetupRefresh("testexamples/");
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
	TestSetup.testSetupRefresh("testexamples/");
    }
    
    @Test
    public void testRunMethodAdditionPolymorphic() throws Throwable {
	String mainClass = "testexamples.methodadditionpolymorphic.UserPolyTest";
        String[] classes = {mainClass,
			    "testexamples.methodadditionpolymorphic.AdditionMethodPolymorphicTestChildOne",
			    "testexamples.methodadditionpolymorphic.AdditionMethodPolymorphicTestChildThree",
			    "testexamples.methodadditionpolymorphic.AdditionMethodPolymorphicTestChildSix"};

        TestSetup.makeListFiles(classes);
        String[] differArgs =  TestSetup.setup(mainClass);
        TestSetup.runAdapter(Arrays.asList(mainClass), differArgs);

        Class<?> clazz = TestSetup.validateClassFile(mainClass);

        Object obj = clazz.newInstance();
	
	java.lang.reflect.Method methodHandleOne = clazz.getMethod("use", int.class);
        Assert.assertNotNull(methodHandleOne);
        Assert.assertEquals(0, methodHandleOne.invoke(obj, 0));
	Assert.assertEquals(1, methodHandleOne.invoke(obj, 1));
	Assert.assertEquals(1, methodHandleOne.invoke(obj, 5));
	Assert.assertEquals(3, methodHandleOne.invoke(obj, 3));
        Assert.assertEquals(6, methodHandleOne.invoke(obj, 6));
	Assert.assertEquals(2, methodHandleOne.invoke(obj, 2));
        Assert.assertEquals(2, methodHandleOne.invoke(obj, 4));
	TestSetup.testSetupRefresh("testexamples/methodadditionpolymorphic");
    }
}

