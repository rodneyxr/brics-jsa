package dk.brics.string.java;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import soot.Body;
import soot.SootMethod;
import soot.Trap;
import soot.Unit;
import soot.ValueBox;
import soot.jimple.IfStmt;
import soot.jimple.LookupSwitchStmt;
import soot.jimple.Stmt;
import soot.jimple.toolkits.annotation.nullcheck.NullnessAnalysis;
import soot.tagkit.LineNumberTag;
import soot.tagkit.Tag;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.graph.ExceptionalUnitGraph;
import soot.toolkits.scalar.LiveLocals;
import soot.toolkits.scalar.LocalDefs;
import soot.toolkits.scalar.SimpleLiveLocals;
import soot.toolkits.scalar.SmartLocalDefs;
import dk.brics.string.intermediate.Call;
import dk.brics.string.intermediate.Catch;
import dk.brics.string.intermediate.Hotspot;
import dk.brics.string.intermediate.Method;
import dk.brics.string.intermediate.Return;
import dk.brics.string.intermediate.Statement;

public class MethodTranslatorImpl implements MethodTranslator { 
	
	private TranslationContext jt;
	private StatementTranslatorFacade statementTranslator;
	private SootMethod sootMethod;
	private NullnessAnalysis nullAnalysis;
	private String currentSourceFile;
	private Map<Trap, Catch> catchers = new HashMap<Trap, Catch>();
	
	// TODO: Put assertionCreator into the constructor and abstract factory
	private AssertionCreator assertionCreator = new AssertionCreatorImpl();
	
	private int currentLine;
	
	private List<HotspotInfo> currentHotspots;
	
	private Map<Stmt, TranslatedStatement> translations = new HashMap<Stmt, TranslatedStatement>();
	
	public MethodTranslatorImpl(StatementTranslatorFacade facade) {
		this.statementTranslator = facade;
	}
	
	private void findSourceFile() {
		Tag tag = sootMethod.getDeclaringClass().getTag("SourceFileTag");
		if (tag != null) {
			currentSourceFile = tag.toString();
		} else {
			currentSourceFile = "";
		}
	}
	
	public List<HotspotInfo> translateMethod(SootMethod sootMethod, TranslationContext jt) {
		// set state
		this.jt = jt;
		this.sootMethod = sootMethod;
		this.translations.clear();
		this.catchers.clear();
		this.currentHotspots = new LinkedList<HotspotInfo>();
		findSourceFile();
		
		Method method = jt.getMethod(sootMethod);
		Body body = sootMethod.retrieveActiveBody();
		
		// use an exceptional unit graph for the nullness analysis
        ExceptionalUnitGraph exceptionalFlow = new ExceptionalUnitGraph(body);
        nullAnalysis = new NullnessAnalysis(exceptionalFlow);
        
        // prepare the reaching definitions analysis for the assertion creator
        LiveLocals liveness = new SimpleLiveLocals(exceptionalFlow);
        LocalDefs definitions = new SmartLocalDefs(exceptionalFlow, liveness);
        
        // translate each statement in isolation
        for (Unit unit : body.getUnits()) {
        	Stmt stmt = (Stmt) unit;
        	translateStmt(stmt);
        }
		
		// create intermediate Catch statements for every catch block
		for (Trap trap : body.getTraps()) {
			Catch ct = new Catch();
			method.addStatement(ct);
			
			// remember the Catch statement associated with the trap
			catchers.put(trap, ct);
			
			// add the catch block as successor
			ct.addSucc(translations.get(trap.getHandlerUnit()).getFirst());
		}
        
        // connect according to normal flow
        AssertionContext assertionContext = new AssertionContext(jt, definitions, translations, sootMethod);
        BriefUnitGraph normalFlow = new BriefUnitGraph(body);
        for (Unit stmt : body.getUnits()) {
            Statement tail = translations.get(stmt).getLast();
            
            if (stmt instanceof IfStmt) {
                // branching statement: link assertion in-between its successors
                IfStmt ifstmt = (IfStmt)stmt;
                
                Stmt trueSuccessor = ifstmt.getTarget();
                Stmt falseSuccessor = (Stmt)body.getUnits().getSuccOf(ifstmt);
                AssertionBranches assertions = assertionCreator.createAssertions(ifstmt, assertionContext);
                
                tail.addSucc(assertions.getWhenFalse().getFirst());
                tail.addSucc(assertions.getWhenTrue().getFirst());
                
                assertions.getWhenFalse().getLast().addSucc(translations.get(falseSuccessor).getFirst());
                assertions.getWhenTrue().getLast().addSucc(translations.get(trueSuccessor).getFirst());
            } 
            else if (stmt instanceof LookupSwitchStmt) {
            	LookupSwitchStmt sw = (LookupSwitchStmt)stmt;
            	
            	// add cases
            	List<Integer> values = new ArrayList<Integer>();
            	for (int i=0; i<sw.getTargetCount(); i++) {
            		Stmt succ = (Stmt)sw.getTarget(i);
            		AssertionBranch assertion = assertionCreator.createSwitchAssertions(sw.getKeyBox(), sw.getLookupValue(i), sw, assertionContext);
            		
            		tail.addSucc(assertion.getFirst());
            		assertion.getLast().addSucc(translations.get(succ).getFirst());
            		
            		values.add(sw.getLookupValue(i));
            	}
            	
            	// add default case
            	AssertionBranch assertion = assertionCreator.createSwitchDefaultAssertions(sw.getKeyBox(), values, sw, assertionContext);
            	tail.addSucc(assertion.getFirst());
            	assertion.getLast().addSucc(translations.get(sw.getDefaultTarget()).getFirst());
            }
            else {
                // normal statement
            	for (Unit succ : normalFlow.getSuccsOf(stmt)) {
            	    tail.addSucc(translations.get(succ).getFirst());
            	}
            }
        }
        
        // connect first statements to the head
        for (Unit stmt : normalFlow.getHeads()) {
        	method.getEntry().addSucc(translations.get(stmt).getFirst());
        }
        
        // connect according to exceptional flow
        List<Catch> activeCatchers = new LinkedList<Catch>();
        for (Unit stmt : body.getUnits()) {
        	// open and close catchers
        	for (Trap trap : body.getTraps()) {
        		if (trap.getBeginUnit() == stmt) {
        			activeCatchers.add(catchers.get(trap));
        		}
        		if (trap.getEndUnit() == stmt) {
        			activeCatchers.remove(catchers.get(trap));
        		}
        	}
        	
        	// if statement S might throw an exception, an edge from its 
        	// predecessors must go to the exceptional return and/or catcher.
        	
        	// set exceptional flow inside the translation (but not after)
        	for (Statement stm : translations.get(stmt).getStatements()) {
        		// return statements have no successors
        		if (stm instanceof Return)
        			continue;
        		
        		// exceptions don't get thrown if the statement completed
        		// Call statements, however, may always throw an exception
        		if (stm == translations.get(stmt).getLast() && !(stm instanceof Call))
        			continue;
        		
        		for (Catch catcher : activeCatchers) {
        			stm.addSuccIfAbsent(catcher);
        		}
        		stm.addSuccIfAbsent(method.getExceptionalReturn());
        	}
        	
        	// set exceptional flow if the block fails immediately (before the first)
        	for (Statement stm : translations.get(stmt).getFirst().getPreds()) {
        		// avoid adding duplicate edges, so check if the exceptional edge is already there
        		for (Catch catcher : activeCatchers) {
        			stm.addSuccIfAbsent(catcher);
        		}
        		stm.addSuccIfAbsent(method.getExceptionalReturn());
        	}
        }
        
        return currentHotspots;
	}
	
	private void translateStmt(Stmt stmt) {
        for (Tag tag : stmt.getTags()) {
            if (tag instanceof LineNumberTag) {
            	currentLine = Integer.parseInt(tag.toString());
            }
        }
        
        TranslatedStatement translation = statementTranslator.translateStatement(stmt, sootMethod, nullAnalysis, jt);
        
        // add all the hotspots we found
        for (HotspotValueBoxPair hotspot : translation.getHotspots()) {
        	addHotspot(hotspot.getHotspot(), hotspot.getBox());
        }
        
        // validate the translation object
        //validateTranslation(translation); // only for debugging
        
        // remember the entry- and exit points
        translations.put(stmt, translation);
    }
	
	private void addHotspot(Hotspot hotspot, ValueBox box) {
		HotspotInfo info = new HotspotInfo(hotspot, box);
		info.setLineNumber(currentLine);
		info.setMethodName(sootMethod.getName());
		info.setClassName(sootMethod.getDeclaringClass().getName());
		info.setSourcefile(currentSourceFile);
		currentHotspots.add(info);
	}
	
	
	/**
	 * This method is here for debugging. It is not used, but we keep it here
	 * because it is useful to enable sometimes.
	 * @author Asger
	 */
	@SuppressWarnings("unused")
    private void validateTranslation(TranslatedStatement translation) {
		// ensure every path from the first statement reaches the last statement
		LinkedList<Statement> queue = new LinkedList<Statement>();
		Set<Statement> reaching = new HashSet<Statement>(); // statements that will definitely reach the end
		reaching.add(translation.getLast());
		queue.add(translation.getLast());
		
		// color all nodes reachable using predecessor edges from the last statement
		while (!queue.isEmpty()) {
			Statement stm = queue.removeFirst();
			for (Statement pred : stm.getPreds()) {
				if (reaching.contains(pred))
					continue;
				reaching.add(pred);
				queue.add(pred);
			}
		}
		// see if we can find a non-colored node
		Set<Statement> seen = new HashSet<Statement>();
		queue.add(translation.getFirst());
		while (!queue.isEmpty()) {
			Statement stm = queue.removeFirst();
			if (!reaching.contains(stm))
				throw new RuntimeException("Invalid statement translation. This statement cannot not reach the end: " + stm);
			seen.add(stm);
			for (Statement succ : stm.getSuccs()) {
				if (seen.contains(succ))
					continue;
				queue.add(succ);
			}
		}
	}
}
