package differ;

import java.util.Arrays;
import java.util.List;
import java.util.Iterator;
import java.util.HashMap;
import java.util.Map;

import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.ExplicitEdgesPred;
import soot.jimple.toolkits.callgraph.Filter;
import soot.jimple.toolkits.callgraph.Targets;

import soot.Modifier;
import soot.Scene;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.SootMethodRef;
import soot.Body;
import soot.Unit;
import soot.PatchingChain;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.DefinitionStmt;
import soot.Type;
import soot.VoidType;
import soot.Local;
import soot.ValueBox;
import soot.Value;
import soot.util.Chain;
import soot.jimple.Jimple;
import soot.jimple.JimpleBody;
	

public class PatchTransformer{

	private SootClass newClass;
	private boolean newClassHasStaticInit = false;
	private Filter explicitInvokesFilter;
	//idk if this is best or second element on one hashmap should be list
	private HashMap<SootField, SootMethod> fieldToGetter = new HashMap<SootField, SootMethod>();
	private HashMap<SootField, SootMethod> fieldToSetter = new HashMap<SootField, SootMethod>();
	private HashMap<SootField, SootField> oldFieldToNew = new HashMap<SootField, SootField>();

	private HashMap<SootMethodRef, SootMethodRef> oldMethodToNew = new HashMap<SootMethodRef, SootMethodRef>(); 
	
	public PatchTransformer(SootClass newClass){
		this.newClass = newClass;
		explicitInvokesFilter = new Filter(new ExplicitEdgesPred());
	}

	
	public void transformMethodCalls(SootClass redefinition, List<SootMethod> addedMethods){
		//remove the added methods from redefinition class
		//and place into wrapper class
		for(SootMethod m : addedMethods){
			SootMethodRef oldRef = m.makeRef();
			redefinition.removeMethod(m);
			newClass.addMethod(m);
			oldMethodToNew.put(oldRef, m.makeRef());
		}
		System.out.println("THISIS METHOD MAP");
		System.out.println(oldMethodToNew);
		for(Map.Entry<SootMethodRef, SootMethodRef> m : oldMethodToNew.entrySet()){
			System.out.println(m.getKey() + " "+ m.getValue());
		}
		//then patch the invokes, in two parts
		for(SootMethod m : addedMethods){
			//for calls in the added methods ,we cannot find a change set, so do our best and guard the rest
			findMethodCalls(m);
		}
		for(SootMethod m : redefinition.getMethods()){
            //find a change set and patch what we can and guard the rest, but only in the change set
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
				//	while(targets.hasNext()){
				//	System.out.println(targets.next());
				//}
				System.out.println(".....................................");
				if(targets.hasNext()){
					SootMethod target = (SootMethod) targets.next();
					if(oldMethodToNew.get(invokeExpr.getMethodRef()) != null && !targets.hasNext()){
						System.out.println("replacing a method call in this statement: "+ s);
						System.out.println(invokeExpr.getMethodRef() + " ---> " + oldMethodToNew.get(invokeExpr.getMethodRef()));
						//condition where the cg has only one explicit target for this call
						invokeExpr.setMethodRef(oldMethodToNew.get(invokeExpr.getMethodRef()));
					}
				}else{
					System.out.println("Did not do anything with this statment: " + s);
				}
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
	}

	//adds the field to the new class and if the field was private constructs an accessor for it
	private void fixFields(SootField field){
		//cant actually just move the same field ref, need it to exist in both classes simultaneously in order to fix refs
		SootField newField = new SootField(field.getName(), field.getType(), field.getModifiers());
		newClass.addField(newField); 
		oldFieldToNew.put(field, newField);
		
		System.out.println("is the newfield static?: "+ newField.isStatic());
		System.out.println("is the newfield phantom for some reason?:"+ newField.isPhantom());

		//construct getter and setter for even public fields
		String methodName =	"get"+ field.getName();

		//getter first
			//need the acessor to be public , might need to also be static
			int modifiers = Modifier.PROTECTED;
			if(field.isStatic()){
				modifiers |= Modifier.STATIC;
			}
			SootMethod newGetter = new SootMethod(methodName, Arrays.asList(new Type[]{}), field.getType(), modifiers);

			JimpleBody getterBody = Jimple.v().newBody(newGetter);
			newGetter.setActiveBody(getterBody);
			Chain units = getterBody.getUnits();

			//must create local then can return that
			Local tmpref =  Jimple.v().newLocal("tmpref", field.getType());
			getterBody.getLocals().add(tmpref);
			units.add(Jimple.v().newAssignStmt(tmpref, Jimple.v().newStaticFieldRef(newField.makeRef())));
			units.add(Jimple.v().newReturnStmt(tmpref));
			
			newClass.addMethod(newGetter);
			fieldToGetter.put(field, newGetter);

			//make setter too
			String setterMethodName = "set"+ field.getName();
			//may have some issue here if there was casting for stmts we will replace...
			SootMethod newSetter = new SootMethod(setterMethodName, Arrays.asList(new Type[]{field.getType()}), VoidType.v(), modifiers);

            JimpleBody setterBody = Jimple.v().newBody(newSetter);
			newSetter.setActiveBody(setterBody);
			Chain setterUnits = setterBody.getUnits();
			//have to assign the param to a local first, cannot go directly from param to assignstmt, idk exactly why
			Local paramref =  Jimple.v().newLocal("paramref", field.getType());
			setterBody.getLocals().add(paramref);
			setterUnits.add(Jimple.v().newIdentityStmt(paramref, Jimple.v().newParameterRef(field.getType(), 0)));
			setterUnits.add(Jimple.v().newAssignStmt(Jimple.v().newStaticFieldRef(newField.makeRef()), paramref));
			setterUnits.add(Jimple.v().newReturnVoidStmt());
			newClass.addMethod(newSetter);
			fieldToSetter.put(field, newSetter);
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
					SootField newref = oldFieldToNew.get(ref);

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
					
					if(newref != null){

						//must steal initialization if the field is static
						// is the second check even needed?
						if(m.getName().equals("<clinit>") && ref.isStatic()){
							units.remove(u);
							createStaticInitializer();
							System.out.println("This is wahts in the box atm: " + s.getFieldRefBox().getValue());
							s.getFieldRefBox().setValue(Jimple.v().newStaticFieldRef(oldFieldToNew.get(ref).makeRef()));
							newClass.getMethod("<clinit>", Arrays.asList(new Type[]{}), VoidType.v()).retrieveActiveBody().getUnits().addFirst(u);

						}else {

							ValueBox fieldref = s.getFieldRefBox();
							if(u.getUseBoxes().contains(fieldref)){

								SootMethod newAccessor = fieldToGetter.get(ref);
								System.out.println("doing a field ref replace: " + ref + " --->" + newAccessor);
								System.out.println("in this statement: "+ s);
								
								Local tmpRef = Jimple.v().newLocal("tmpRef", ref.getType());
								body.getLocals().add(tmpRef);
								
								units.insertBefore(Jimple.v().newAssignStmt(tmpRef, Jimple.v().newStaticInvokeExpr(newAccessor.makeRef())), u);

								s.getFieldRefBox().setValue(tmpRef);
								
							}
							if(u.getDefBoxes().contains(fieldref)){

								SootMethod newAccessor = fieldToSetter.get(ref);
								System.out.println("doing a field ref replace: " + ref + " --->" + newAccessor);
								System.out.println("in this statement: "+ s);
								//def boxes only to be nonempty on identitystmts or assignstmts
								units.insertBefore(Jimple.v().newInvokeStmt(Jimple.v().newStaticInvokeExpr(newAccessor.makeRef(), Arrays.asList( new Value[]{((DefinitionStmt)s).getRightOp()}))) , u);
								units.remove(u);
								

							}
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
