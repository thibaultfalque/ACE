/*
 * This file is part of the constraint solver ACE (AbsCon Essence). 
 *
 * Copyright (c) 2021. All rights reserved.
 * Christophe Lecoutre, CRIL, Univ. Artois and CNRS. 
 * 
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */

package solver;

import static org.xcsp.common.Types.TypeFramework.COP;
import static org.xcsp.common.Types.TypeFramework.CSP;
import static org.xcsp.common.Types.TypeFramework.MAXCSP;
import static utility.Kit.control;
import static utility.Kit.log;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.xcsp.common.Constants;
import org.xcsp.common.Types.TypeFramework;
import org.xcsp.modeler.entities.VarEntities.VarAlone;
import org.xcsp.modeler.entities.VarEntities.VarArray;
import org.xcsp.modeler.entities.VarEntities.VarEntity;

import constraints.Constraint;
import main.HeadExtraction;
import problem.Problem;
import solver.Solver.Stopping;
import utility.Kit;
import utility.Kit.Color;
import variables.Variable;

/**
 * The object used to record and display solutions (and bounds, etc.)
 * 
 * @author Christophe Lecoutre
 */
public final class Solutions {

	/**
	 * The solver to which this object is attached
	 */
	public final Solver solver;

	/**
	 * The maximum number of solutions to be found, before stopping. When equal to PLUS_INFINITY, all solutions are searched for (no limit is fixed).
	 */
	public long limit;

	/**
	 * The number of solutions found by the solver so far. Initially, 0.
	 */
	public long found;

	/**
	 * For optimization problems, the first bound found by the solver if found > 0 (otherwise, 0)
	 */
	public long firstBound;

	/**
	 * For optimization problems, the best bound found by the solver if found > 0, the specified upper bound initially given otherwise
	 */
	public long bestBound;

	/**
	 * The last found solution (array containing indexes of values, and not values), or null
	 */
	public int[] last;

	/**
	 * The number of the run where the last solution has been found, or -1 if no solution has been found
	 */
	public int lastRun = -1;

	/**
	 * Stores all solutions found by the solver, if activated
	 */
	private final List<int[]> store;

	/**
	 * The object used to output solutions in XML
	 */
	private XML xml;

	/**
	 * An object used for competitions
	 */
	private AtomicBoolean lock = new AtomicBoolean();

	/**
	 * The list of ids of variables (entities) that must not be displayed (when outputting solutions)
	 */
	public List<String> undisplay = new ArrayList<>();

	private int[] hamming;

	private int hammingOpt;

	/**
	 * Class for outputting solutions in XML
	 */
	private class XML {

		/**
		 * The string used to display a solution in XML. It lists variables of the problem (but not the auxiliary variables that are been automatically
		 * introduced).
		 */
		private final String xmlVars;

		private XML() {
			this.xmlVars = vars(true);
		}

		private void updateCompactList(int value, int cnt, List<String> ls) {
			String v = value == Constants.STAR ? Constants.STAR_SYMBOL : value + "";
			if (cnt > 2) // hard coding
				ls.add(v + Constants.TIMES + cnt);
			else
				for (int k = 0; k < cnt; k++)
					ls.add(v);
		}

		private void updateList(Object object, List<Integer> list) {
			if (object == null)
				list.add(Constants.STAR);
			else if (object instanceof Variable) {
				Variable x = (Variable) object;
				if (solver.problem.features.collecting.variables.contains(x))
					list.add(x.dom.toVal(last[x.num])); // ((Variable) object).valueIndexInLastSolution));
				else if (x == solver.problem.replacedObjVar)
					list.add((int) bestBound);
			} else // recursive call
				Stream.of((Object[]) object).forEach(o -> updateList(o, list));
		}

		private String compactValues(boolean discardAuxiliary) {
			control(solver.problem.features.nSymbolicVars == 0);
			List<String> compactList = new ArrayList<>();
			for (VarEntity va : solver.problem.varEntities.allEntities) {
				if (undisplay.contains(va.id) || (discardAuxiliary && va.id.startsWith(Problem.AUXILIARY_VARIABLE_PREFIX)))
					continue;
				if (compactList.size() > 0)
					compactList.add(" ");
				List<Integer> list = new ArrayList<>();
				updateList(va instanceof VarAlone ? ((VarAlone) va).var : VarArray.class.cast(va).vars, list);
				int prev = list.get(0);
				int cnt = 1;
				for (int i = 1; i < list.size(); i++) {
					if (list.get(i) == prev)
						cnt++;
					else {
						updateCompactList(prev, cnt, compactList);
						prev = list.get(i);
						cnt = 1;
					}
				}
				updateCompactList(prev, cnt, compactList);
			}
			return compactList.stream().collect(Collectors.joining(" "));
		}

		private String vals(boolean compact, boolean discardAuxiliary) {
			if (compact && solver.problem.features.nSymbolicVars == 0)
				return compactValues(discardAuxiliary);
			StringBuilder sb = new StringBuilder();
			for (VarEntity va : solver.problem.varEntities.allEntities) {
				if (undisplay.contains(va.id) || (discardAuxiliary && va.id.startsWith(Problem.AUXILIARY_VARIABLE_PREFIX)))
					continue;
				if (sb.length() > 0)
					sb.append("  ");
				if (va instanceof VarAlone) {
					Variable x = (Variable) ((VarAlone) va).var;
					sb.append(x.dom.prettyValueOf(x == solver.problem.replacedObjVar ? (int) bestBound : last[x.num])); // .valueIndexInLastSolution));
				} else
					sb.append(Variable.rawInstantiationOf(VarArray.class.cast(va).vars));
			}
			return sb.toString();
		}

		private String vars(boolean discardAuxiliary) {
			StringBuilder sb = new StringBuilder();
			for (VarEntity va : solver.problem.varEntities.allEntities) {
				if (undisplay.contains(va.id) || (discardAuxiliary && va.id.startsWith(Problem.AUXILIARY_VARIABLE_PREFIX)))
					continue;
				sb.append(sb.length() > 0 ? " " : "").append(va.id).append(va instanceof VarArray ? ((VarArray) va).getEmptyStringSize() : "");
			}
			return sb.toString();
		}

		/**
		 * @return the last found solution in XML format
		 */
		private String lastSolution() { // note that auxiliary variables are not considered
			assert found > 0;
			StringBuilder sb = new StringBuilder("<instantiation id='sol").append(found).append("' type='solution'");
			sb.append(solver.problem.framework != CSP ? " cost='" + bestBound + "'" : "").append(">");
			sb.append(" <list> ").append(xmlVars).append(" </list> <values> ");
			sb.append(vals(solver.problem.options.xmlCompact, true));
			return sb.append(" </values> </instantiation>").toString();
		}
	}

	/**
	 * @return the last found solution in JSON format
	 */
	private String lastSolutionInJsonFormat(boolean pure) {
		assert found > 0;
		boolean discardAuxiliary = !solver.head.control.general.jsonAux;
		String PREFIX = pure ? "" : "   ";
		StringBuilder sb = new StringBuilder(PREFIX).append("{\n");
		for (VarEntity va : solver.problem.varEntities.allEntities) {
			if (undisplay.contains(va.id) || (discardAuxiliary && va.id.startsWith(Problem.AUXILIARY_VARIABLE_PREFIX)))
				continue;
			sb.append(PREFIX).append(" ").append(pure ? "'" : "").append(va.id).append(pure ? "'" : "").append(": ");
			if (va instanceof VarAlone) {
				Variable x = (Variable) ((VarAlone) va).var;
				if (solver.problem.features.collecting.variables.contains(x))
					sb.append(x.dom.prettyValueOf(last[x.num])); // valueIndexInLastSolution));
				else if (x == solver.problem.replacedObjVar)
					sb.append(bestBound);
			} else
				sb.append(Variable.instantiationOf(VarArray.class.cast(va).vars, PREFIX));
			sb.append(",\n");
		}
		sb.delete(sb.length() - 2, sb.length());
		return sb.append("\n").append(PREFIX).append("}").toString();
	}

	/**
	 * Builds an object handling solutions found by the specified solver
	 * 
	 * @param solver
	 *            the solver to which this object is attached
	 * @param limit
	 *            the maximum number of solutions to be found
	 */
	public Solutions(Solver solver, long limit) {
		this.solver = solver;
		this.limit = limit;
		this.bestBound = solver.problem.optimizer == null || solver.problem.optimizer.minimization ? solver.head.control.optimization.ub
				: solver.head.control.optimization.lb;
		this.store = null; // solver.head.control.general.recordSolutions ? new ArrayList<>() : null;
		this.xml = new XML();
		Runtime.getRuntime().addShutdownHook(new Thread(() -> displayFinalResults()));
		this.hamming = new int[solver.problem.varArrays.length + 2]; // +2 for stand-alone variables and solver auxiliary variables
	}

	/**
	 * Displays final results when the solving process is finished (possibly, interrupted).
	 */
	public void displayFinalResults() {
		TypeFramework framework = solver.problem.framework;
		boolean fullExploration = solver.stopping == Stopping.FULL_EXPLORATION;
		if (solver.head instanceof HeadExtraction) {
			HeadExtraction head = (HeadExtraction) solver.head;
			for (List<Constraint> core : head.cores) {
				Color.GREEN.println("\nc CORE",
						" #C=" + core.size() + " #V=" + core.stream().collect(Collectors.toCollection(HashSet::new)).size() + " => { " + Kit.join(core) + " }");
			}
			Color.GREEN.println("d NRUNS " + head.nRuns);
		} else {
			// if (found > 0 && last == null)
			// return;
			synchronized (lock) {
				if (!lock.get()) {
					lock.set(true);
					if (solver.profiler != null)
						solver.profiler.display(solver.problem.constraints);
					System.out.println();
					if (solver.head.control.general.verbose >= 0 && found > 0 && solver.problem.variables.length <= solver.head.control.general.jsonLimit)
						System.out.println(
								"\n  Solution " + found + " in JSON format:\n" + lastSolutionInJsonFormat(solver.head.control.general.jsonQuotes) + "\n");
					if (fullExploration)
						Color.GREEN.println(found == 0 ? "s UNSATISFIABLE" : framework == COP ? "s OPTIMUM FOUND" : "s SATISFIABLE");
					else if (found == 0)
						Color.RED.println("s UNKNOWN");
					else
						Color.GREEN.println("s SATISFIABLE");
					if (!solver.head.control.general.xmlEachSolution && found > 0)
						Color.GREEN.println("\nv", " " + xml.lastSolution());
					Color.GREEN.println("\nd WRONG DECISIONS", " " + (solver.stats != null ? solver.stats.nWrongDecisions : 0));
					Color.GREEN.println("d FOUND SOLUTIONS", " " + found);
					if (framework == COP && found > 0)
						Color.GREEN.println("d BOUND", " " + (bestBound + solver.problem.optimizer.gapBound));
					if (fullExploration)
						Color.GREEN.println("d COMPLETE EXPLORATION");
					else
						Color.RED.println("d INCOMPLETE EXPLORATION");
					Kit.log.config("\nc real time : " + solver.head.stopwatch.cpuTimeInSeconds());
					System.out.flush();

				}
			}
		}
	}

	private void record(int[] t) {
		Variable[] variables = solver.problem.variables;
		assert t == null || t.length == variables.length;
		last = last == null ? new int[variables.length] : last;
		for (int i = 0; i < last.length; i++)
			last[i] = t != null ? t[i] : variables[i].dom.single();
		if (store != null)
			store.add(last.clone());

		// SumSimpleLE c = (SumSimpleLE) solver.pb.optimizer.ctr; Variable x = c.mostImpacting();
		// System.out.println("ccccc most " + x + " " + x.dom.toVal(lastSolution[x.num]));
	}

	public Variable[] h1 = new Variable[0];

	private int h2 = -1;

	private void solutionHamming() {
		if (found <= 1)
			return;
		h1 = IntStream.range(0, last.length).filter(i -> last[i] != solver.problem.variables[i].dom.single()).mapToObj(i -> solver.problem.variables[i])
				.sorted((x, y) -> x.assignmentLevel - y.assignmentLevel).toArray(Variable[]::new); // count();
		if (solver.problem.optimizer != null) {
			Constraint c = (Constraint) solver.problem.optimizer.ctr;
			h2 = (int) IntStream.range(0, last.length)
					.filter(i -> last[i] != solver.problem.variables[i].dom.single() && c.involves(solver.problem.variables[i])).count();
		}
	}

	private void hammingInformation() {
		if (found <= 1)
			return;
		// int h = (int) IntStream.range(0, last.length).filter(i -> last[i] != solver.problem.variables[i].dom.single()).count();
		// int hopt = -1;
		// if (solver.problem.optimizer != null) {
		// Constraint c = (Constraint) solver.problem.optimizer.ctr;
		// hopt = (int) IntStream.range(0, last.length)
		// .filter(i -> last[i] != solver.problem.variables[i].dom.single() && c.involves(solver.problem.variables[i])).count();
		// }

		StringBuilder sb = new StringBuilder();
		int i = 0;
		for (VarArray va : solver.problem.varArrays) {
			if (va.flatVars != null) {
				int v = (int) Stream.of(va.flatVars).filter(x -> last[((Variable) x).num] != solver.problem.variables[((Variable) x).num].dom.single()).count();
				hamming[i++] = v;
				if (v > 0)
					sb.append(" ").append(va.id).append(":").append(v);
			}
		}

		int v = (int) Stream.of(solver.problem.varAlones).filter(x -> last[x.num] != solver.problem.variables[x.num].dom.single()).count();
		hamming[i++] = v;
		if (v > 0)
			sb.append(" ").append("aln").append(":").append(v);

		v = (int) Stream.of(solver.problem.auxiliaryVars).filter(x -> last[x.num] != solver.problem.variables[x.num].dom.single()).count();
		hamming[i++] = v;
		if (v > 0)
			sb.append(" ").append(Problem.AUXILIARY_VARIABLE_PREFIX).append(":").append(v);

		assert IntStream.of(hamming).sum() == IntStream.range(0, last.length).filter(j -> last[j] != solver.problem.variables[j].dom.single()).count();

		if (solver.problem.optimizer != null) {
			hammingOpt = (int) Stream.of(((Constraint) solver.problem.optimizer.ctr).scp)
					.filter(x -> last[x.num] != solver.problem.variables[x.num].dom.single()).count();
		}
	}

	/**
	 * This method must be called whenever a new solution is found by the solver.
	 * 
	 * @param controlSolution
	 *            indicates if the solution must be checked
	 */
	public void handleNewSolution(boolean controlSolution) {
		control(!controlSolution || controlFoundSolution());
		found++;
		// if (found == 1) {
		// Constraint c = ((Constraint) solver.problem.optimizer.ctr);
		// boolean minimization = solver.problem.optimizer.minimization;
		// for (Variable x : c.scp)
		// x.heuristic = minimization ? new First(x, false) : new Last(x, false); // the boolean is dummy
		// Variable[] t = HeuristicValues.prioritySumVars(c.scp, null);
		// // Variable[] vars = HeuristicValues.possibleOptimizationInterference(solver.problem);
		// // solver.problem.priorityVars = c.scp;
		// solver.heuristic.priorityVars = t;
		// // solver.heuristic = new Rand(solver, false);
		// System.out.println("changing to Rand");
		// }
		lastRun = solver.restarter.numRun;
		solutionHamming();
		hammingInformation();
		if (found >= limit)
			solver.stopping = Stopping.REACHED_GOAL;
		if (solver.propagation.performingProperSearch) {
			if (solver.problem.optimizer != null) { // COP
				long bound = solver.problem.optimizer.value();
				if (solver.problem.optimizer.minimization && bound < bestBound || !solver.problem.optimizer.minimization && bound > bestBound) {
					bestBound = bound;
					Color.GREEN.println("o " + solver.problem.optimizer.valueWithGap(), "  " + (solver.head.instanceStopwatch.wckTimeInSeconds()));
					record(null);
				}
			} else
				record(null);
			return;
		}
		record(null);
		solver.stats.times.onNewSolution();
		if (solver.problem.framework == MAXCSP) {
			int z = (int) Stream.of(solver.problem.constraints).filter(c -> !c.isSatisfiedByCurrentInstantiation()).count();
			control(z < bestBound, () -> "z=" + z + " bb=" + bestBound);
			bestBound = z;
		} else if (solver.problem.optimizer != null) { // COP
			bestBound = solver.problem.optimizer.value();
			if (found == 1)
				firstBound = bestBound;
			Color.GREEN.println("o " + solver.problem.optimizer.valueWithGap(), "  " + (solver.head.instanceStopwatch.wckTimeInSeconds()) + "  ham="
					+ IntStream.of(hamming).sum() + " (" + Kit.join(hamming) + ")" + "  opth=" + hammingOpt);

			// solver.restarter.currCutoff += 1; //20;
			// System.out.println("h1 : " + Kit.join(h1) + " h2 : " + h2);
			// for (Variable x : h1) System.out.println(x + " " + x.assignmentLevel);
		}
		// The following code must stay after recording/storing the solution
		if (solver.head.control.general.jsonSave.length() > 0 && (solver.head.control.general.verbose > 1 || found == 1)) {
			String s = lastSolutionInJsonFormat(true);
			try (PrintWriter out = new PrintWriter(solver.head.control.general.jsonSave + found + ".json")) {
				out.println(s);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
		}
		if (solver.head.control.general.verbose > 1 || solver.head.control.general.jsonEachSolution)
			log.config(lastSolutionInJsonFormat(solver.head.control.general.jsonQuotes) + "\n");
		if (solver.head.control.general.verbose > 2 || solver.head.control.general.xmlEachSolution)
			Color.GREEN.println("v", " " + xml.lastSolution());
		// solver.problem.api.prettyDisplay(vars_values(false, false).split("\\s+"));
	}

	/**
	 * This method must be called whenever a new solution is found by the solver
	 */
	public void handleNewSolution() {
		synchronized (lock) {
			if (!lock.get()) {
				lock.set(true);
				handleNewSolution(true);
				lock.set(false);
			}
		}
	}

	private boolean controlFoundSolution() {
		Variable x = Variable.firstNonSingletonVariableIn(solver.problem.variables);
		control(x == null, () -> "Problem with last solution: variable " + x + " has not a unique value");
		if (solver.problem.framework == MAXCSP)
			return true;
		Constraint c = Constraint.firstUnsatisfiedConstraint(solver.problem.constraints);
		control(c == null, () -> {
			int[] vals = Stream.of(c.scp).mapToInt(y -> y.dom.singleValue()).toArray();
			return "Problem with last solution: constraint " + c + " " + c.getClass().getName() + " not satisfied with values : " + Kit.join(vals);
		});
		return true;
	}

}
