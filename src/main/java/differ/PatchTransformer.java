package differ;

import java.util.Arrays;
import java.util.List;
import java.util.Iterator;
import java.util.HashMap;

import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.ExplicitEdgesPred;
import soot.jimple.toolkits.callgraph.Filter;
import soot.jimple.toolkits.callgraph.Targets;

import soot.Modifier;
import soot.Scene;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.Body;
import soot.Unit;
import soot.PatchingChain;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import soot.Type;
import soot.VoidType;
import soot.Local;
import soot.ValueBox;
import soot.util.Chain;
import soot.jimple.Jimple;
import soot.jimple.JimpleBody;
	

public class PatchTransformer{

	private SootClass newClass;
	private boolean newClassHasStaticInit = false;
	private Filter explicitInvokesFilter;
	private HashMap<SootField, SootMethod> fieldToAccessor = new HashMap<SootField, SootMethod>();
	private HashMap<SootField, SootField> oldFieldToNew = new HashMap<SootField, SootField>();
	
	public PatchTransformer(SootClass newClass){
		this.newClass = newClass;
		explicitInvokesFilter = new Filter(new ExplicitEdgesPred());
	}

	
	public void transformMethodCalls(SootClass redefinition, List<SootMethod> addedMethods){
		for(SootMethod m : redefinition.getMethods()){
			findMethodCalls(m);
		}
	}

	private void findMethodCalls(SootMethod m){
		CallGraph cg = Scene.v().getCallGraph();
		Body body;
		System.out.println("Finding method calls in : "+ m.getSignature());
		System.out.println("-------------------------------");
		body = m.retrieveActiveBody(); 
		PatchingChain<Unit> units = body.getUnits();
		for (Unit u: units) { // for each statement
			Stmt s = (Stmt)u;

			if (s.containsInvokeExpr()) {
                InvokeExpr invokeExpr = s.getInvokeExpr();
				System.out.println("Found a method call: "+invokeExpr.getMethodRef() );
				System.out.println("Found in stmt: " + s);
				System.out.println("Printing the targets of this call: ");
				System.out.println(".....................................");
				Iterator targets = new Targets(explicitInvokesFilter.wrap(cg.edgesOutOf(s)));
				System.out.println(targets);
				while(targets.hasNext()){
					System.out.println((SootMethod)targets.next());
					
				}
				System.out.println(".....................................");
			}
		System.out.println("-------------------------------");
		}
	}

	public void transformFields(SootClass redefinition, List<SootField> addedFields){
		//fixFieldRefs(redefinition);
		for(SootField field : addedFields){
			fixFields(field);
		}

		fixFieldRefs(redefinition);

		for(SootField field : addedFields){
			redefinition.removeField(field);
		}
		//have to do this last bc finding the fieldrefs rely on the field still belonging to redef class
		/*		for(SootField field : addedFields){
			System.out.println("Initially: " + field + "is static: "+ field.isStatic());
			redefinition.removeField(field);
			newClass.addField(field);
			System.out.println("After removal then readd: " + field + "is static: "+ field.isStatic());
			}*/
	}

	//adds the field to the new class and if the field was private constructs an accessor for it
	private void fixFields(SootField field){
		//cant actually just move the same field ref, need it to exist in both classes simultaneously in order to fix refs
		SootField newField = new SootField(field.getName(), field.getType(), field.getModifiers());
		newClass.addField(newField); 
		oldFieldToNew.put(field, newField);
		
		System.out.println("is the newfield static?: "+ newField.isStatic());
		System.out.println("is the newfield phantom for some reason?:"+ newField.isPhantom());
		
		if(field.isPrivate()){
            String methodName =	"get"+ field.getName();
			
			//need the acessor to be public , might need to also be static
			int modifiers = Modifier.PUBLIC;
			if(field.isStatic()){
				modifiers |= Modifier.STATIC;
			}
			SootMethod newAccessor = new SootMethod(methodName, Arrays.asList(new Type[]{}), field.getType(), modifiers);

			JimpleBody body = Jimple.v().newBody(newAccessor);
			newAccessor.setActiveBody(body);

			Chain units = body.getUnits();

			//must create local then can return that
			Local tmpref =  Jimple.v().newLocal("tmpref", field.getType());
			body.getLocals().add(tmpref);
			units.add(Jimple.v().newAssignStmt(tmpref, Jimple.v().newStaticFieldRef(newField.makeRef())));
			units.add(Jimple.v().newReturnStmt(tmpref));
			
            newClass.addMethod(newAccessor);
			fieldToAccessor.put(field, newAccessor);
        }
	}

	private void fixFieldRefs(SootClass redefinition){
		for(SootMethod m : redefinition.getMethods()){
			System.out.println("Finding  field refs in : "+ m.getSignature());
			System.out.println("name is: "+ m.getName());
			System.out.println("-------------------------------");
			Body body = m.retrieveActiveBody();
			PatchingChain<Unit> units = body.getUnits();
			Iterator<Unit> it = units.snapshotIterator();

			while (it.hasNext()) {

				Unit u = it.next();
				Stmt s = (Stmt)u;
				if(s.containsFieldRef()){
					SootField ref = s.getFieldRef().getField();
					SootMethod newAccessor = fieldToAccessor.get(ref);

					if(containsUse(u, ref)){
						System.out.println("-------------------------------");
						System.out.println("found a use of: "+ ref);
						System.out.println("in stmt: "+ s);
						System.out.println("-------------------------------");
					}else{
						System.out.println("-------------------------------");
						System.out.println("must have been a def of: "+ ref);
						System.out.println("-------------------------------");
					}
					
					if(newAccessor != null){

						//must steal initialization if the field is static
						// is the second check even needed?
						if(m.getName().equals("<clinit>") && ref.isStatic()){
							units.remove(u);
							createStaticInitializer();
							System.out.println("This is wahts in the box atm: " + s.getFieldRefBox().getValue());
							s.getFieldRefBox().setValue(Jimple.v().newStaticFieldRef(oldFieldToNew.get(ref).makeRef()));
							newClass.getMethod("<clinit>", Arrays.asList(new Type[]{}), VoidType.v()).retrieveActiveBody().getUnits().addFirst(u);

						}else {
						
						System.out.println("doing a field ref replace: " + ref + " --->" + newAccessor);
						System.out.println("in this statement: "+ s);

						Local tmpRef = Jimple.v().newLocal("tmpRef", ref.getType());
						body.getLocals().add(tmpRef);
						
						units.insertBefore(Jimple.v().newAssignStmt(tmpRef, Jimple.v().newStaticInvokeExpr(newAccessor.makeRef())), u);  

						s.getFieldRefBox().setValue(tmpRef);

						}
					}

				}
			}
		}
	}

	private void createStaticInitializer(){
		//only want to create one static initialization function
		if(!newClassHasStaticInit){
			SootMethod initializer = new SootMethod("<clinit>", Arrays.asList(new Type[]{}), VoidType.v(), Modifier.STATIC);
			JimpleBody body = Jimple.v().newBody(initializer);
            initializer.setActiveBody(body);
			Chain units = body.getUnits();
			units.add(Jimple.v().newReturnVoidStmt());
            newClass.addMethod(initializer);
			newClassHasStaticInit = true;
		}
	}

	private boolean containsUse(Unit u, SootField ref){
		System.out.println("starting use check");
		for(ValueBox value : u.getUseBoxes()){
			System.out.println(value.getValue());
			System.out.println(value.getValue().equivHashCode());
			System.out.println(ref.equivHashCode());
			if(value.getValue().equivHashCode() == ref.equivHashCode()){
				return true;
			}
		}
		return false;
	}

}
