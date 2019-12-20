package differ;

import java.util.List;
import java.util.Iterator;

import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.ExplicitEdgesPred;
import soot.jimple.toolkits.callgraph.Filter;
import soot.jimple.toolkits.callgraph.Targets;

import soot.Scene;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.Body;
import soot.Unit;
import soot.PatchingChain;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;

public class PatchTransformer{

	
	private Filter explicitInvokesFilter;
	
	public PatchTransformer(){
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

}
