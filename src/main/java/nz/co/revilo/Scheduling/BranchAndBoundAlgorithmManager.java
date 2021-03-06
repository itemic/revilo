package nz.co.revilo.Scheduling;

import nz.co.revilo.Output.ScheduleResultListener;
import nz.co.revilo.Scheduling.Astar.AstarSchedule;
import nz.co.revilo.Scheduling.Astar.AstarTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Finds optimal schedule using DFS Branch and Bound
 *
 * @author Abby S, Michael K (optimiser, cleaner and documentor), Aimee T
 *
 */
public class BranchAndBoundAlgorithmManager extends AlgorithmManager {

	protected List<Integer> sources = new ArrayList<>();
	protected int[] bottomLevels;
	protected int numNodes;
	protected int totalNodeWeights;
	protected List<Integer> bottomUpSinks = new ArrayList<>();
	protected List<BnBSchedule> rootSchedules = new ArrayList<>();
	protected BnBSchedule optimalSchedule;
	protected List<Integer> nodeStartTimes = new ArrayList<>();
	protected List<Integer> nodeProcessors = new ArrayList<>();
	protected Map<String, Object> existingScheduleStructures = new ConcurrentHashMap<>();
	private static Object stubObject = new Object();

	public BranchAndBoundAlgorithmManager(int processingCores) {
		super(processingCores);
	}

	@Override
	protected void execute() {
		numNodes = _nodeWeights.length;
		bottomLevels = new int[numNodes];
		NeighbourManagerHelper.setUpHelper(numNodes, _arcs);

		for (int nodeId = 0; nodeId < numNodes; nodeId++) {
			//get sources
			if (!NeighbourManagerHelper.hasInneighbours(nodeId)) {
				//if they don't have parents, then add it to a sources queue
				sources.add(nodeId);
			}

			//get sinks
			else if (!NeighbourManagerHelper.hasOutneighbours(nodeId)) {
				bottomUpSinks.add(nodeId);
				bottomLevels[nodeId] = _nodeWeights[nodeId];
			}

			totalNodeWeights += _nodeWeights[nodeId];
		}

		//Take a greedy path down the tree to find a more realistic upper bound
		upperBound.set(greedyUpperBound());
		if ((totalNodeWeights + 1) < upperBound.get()) {
			upperBound.set(totalNodeWeights + 1);
		} else {
			upperBound.incrementAndGet();
		}

		calculateBottomLevels();

		/*
		 * Take turns with each root going on first
		 * Matters when more roots than processors, so some roots can't start at time=0
		 */
		for (int nodeId : sources) {
			BnBSchedule newSchedule = new BnBSchedule(this, null, nodeId, 0);
			rootSchedules.add(newSchedule);
		}    

		startBnb(); //polymorphic call depending on Parallel or not

		returnResults();
	}

	/**
	 * Starts the branch and bound algorithm. To be overridden by child classes which need a different
	 * implementation.
	 * 
	 * @author Aimee
	 */
	protected void startBnb() {
		while(!rootSchedules.isEmpty()){
			bnb(rootSchedules.remove(0));
		}
	}


	/**
	 * Return the optimal schedule found and it's information
	 *
	 * @author Abby S
	 */
	private void returnResults() {
		for (int nodeId = 0; nodeId < numNodes; nodeId++) {
			nodeStartTimes.add(optimalSchedule.closedNodes.get(nodeId).getA());//start times
			nodeProcessors.add(optimalSchedule.closedNodes.get(nodeId).getB());//processors scheduled on
		}
		System.out.println("Optimal length found: " + optimalSchedule.getMaxFinishTime());

		//pass to outputs
		for (ScheduleResultListener listener : getListeners()) {
			listener.finalSchedule(
					_graphName,
					Arrays.asList(_nodeNames),
					PrimitiveInterfaceHelper.primToBoolean2D(_arcs),
					PrimitiveInterfaceHelper.primToInteger2D(_arcWeights),
					PrimitiveInterfaceHelper.primToInteger1D(_nodeWeights),
					nodeStartTimes,
					nodeProcessors
					);
		}
	}

	/**
	 * bnb based on the current schedule s
	 *
	 * @param schedule
	 * @author Abby S, Terran K
	 */
	protected void bnb(final BnBSchedule schedule) {
        exploredStates.incrementAndGet();
		synchronized (this) {
			if (schedule.lowerBound >= upperBound.get()) {
				brokenTrees.incrementAndGet(); //this tree has broken
				return; //break tree at this point
			}
		}

		synchronized (this) {
			//compare to existing schedule structures and remove if duplicate
			if (existingScheduleStructures.containsKey(schedule._scheduleStructureId)) {
				brokenTrees.incrementAndGet(); // this tree has broken
				return; //break tree at this point
			} else {
				existingScheduleStructures.put(schedule._scheduleStructureId, stubObject);
			}
		}

		synchronized (this) {
			//found optimal for the root started with
			//reached end of a valid schedule. Never broke off, so is optimal
			if (schedule.openNodes.isEmpty()) {
				//to make sure only optimal schedules get through
				if (schedule.getMaxFinishTime() < upperBound.get()) {
					setOptimalSchedule(schedule);
					return;
				}
			}
		}

		//continue DFS
		List<BnBSchedule> nextSchedules = new ArrayList<>();
		for (int node : schedule.independentNodes) {
			for (int processor = 0; processor < _processingCores; processor++) {
				nextSchedules.add(new BnBSchedule(this, schedule, node, processor));
			}
		}
		for (BnBSchedule nextSchedule : nextSchedules) {
			bnb(nextSchedule);
		}
	}

	/**
	 * If the schedule found is optimal, set it to be the optimal schedule and notify listeners
	 * 
	 * @author Aimee T
	 */
	protected void setOptimalSchedule(BnBSchedule schedule) {
		optimalSchedule = schedule;

		// if OptimalListener is null it means that we're not actually asking for updates
		// because we are likely not using a visualization
		if (getOptimalListener().get() != null) {
			getOptimalListener().get().newOptimal(optimalSchedule);
		}

		upperBound.set(schedule.getMaxFinishTime());
		atomicBound.set(upperBound.get());
	}

	/**
	 * Calculates bottom level of each node in the graph
	 * Using bottom-up approach
	 *
	 * @author Abby S
	 */
	private void calculateBottomLevels() {
		while (!bottomUpSinks.isEmpty()) {
			int nodeId = bottomUpSinks.remove(0);
			List<Integer> inneighbours = NeighbourManagerHelper.getInneighbours(nodeId);

			for (int inneighbour : inneighbours) {
				//bottom up add its weight to child's
				int fromGivenNode = bottomLevels[nodeId] + _nodeWeights[inneighbour];
				//Farthest distance needed from bottom
				bottomLevels[inneighbour] = bottomLevels[inneighbour] > fromGivenNode ? bottomLevels[inneighbour] : fromGivenNode;

				List<Integer> inneighboursChildren = NeighbourManagerHelper.getOutneighbours(inneighbour); //nodes with 1 on the node's row
				inneighboursChildren.remove(Integer.valueOf(nodeId)); //Integer or will treat the int as index
				if (inneighboursChildren.isEmpty()) {
					bottomUpSinks.add(inneighbour);//become a sink now that child is removed
				}
			}
		}
	}

	/**
	 * Calculates an upper bound using a greedy A* based algorithm
	 * @author Michael Kemp
	 * @return an upper bound
	 */
	private int greedyUpperBound() {
		//Number of Tasks
		int numTasks = _nodeWeights.length;

		//Categorize Tasks into root nodes/tasks and other tasks
		Set<Integer> startTasks = new HashSet<>();
		Set<Integer> otherTasks = new HashSet<>();
		for (int task = 0; task < numTasks; task++) { // init to contain all tasks
			startTasks.add(task);
			otherTasks.add(task);
		}
		for (int fromTask = 0; fromTask < numTasks; fromTask++) { // remove tasks that have parents from start tasks
			for (int toTask = 0; toTask < numTasks; toTask++) {
				if (_arcs[fromTask][toTask]) {
					startTasks.remove(toTask);
				}
			}
		}
		otherTasks.removeAll(startTasks);

		//Determine partial dependencies for every task
		//For every node, add a list of all its parents
		List<List<Integer>> partialDependencies = new ArrayList<>();
		for (int toTask = 0; toTask < numTasks; toTask++) {
			List<Integer> subList = new ArrayList<>();
			for (int fromTask = 0; fromTask < numTasks; fromTask++) {
				if (_arcs[fromTask][toTask]) {
					subList.add(fromTask);
				}
			}
			partialDependencies.add(subList);
		}

		//Initialises root schedule
		//Adds tasks
		AstarSchedule root = new AstarSchedule();
		for (int unschedulableTask : otherTasks) { // non sources are not eligible to be scheduled
			root._unschedulable.add(new AstarTask(unschedulableTask));
		}
		for (int schedulableTask : startTasks) {
			root._schedulable.add(new AstarTask(schedulableTask));
		}
		//Initializes to zeroes when the processors were last used
		root._processorLastUsed = new ArrayList<>(_processingCores);
		for (int processor = 0; processor < _processingCores; processor++) {
			root._processorLastUsed.add(0);
		}

		//Iterative DFS
		//Initialise each level
		List<List<AstarSchedule>> levels = new ArrayList<>(numTasks + 1);
		for (int level = 0; level <= numTasks; level++) {
			levels.add(new ArrayList<>());
		}
		//Calculate
		for (int level = 0; level < numTasks; level++) {
			//Load current and next level
			List<AstarSchedule> currentLevel = levels.get(level);
			List<AstarSchedule> nextLevel = levels.get(level + 1);

			//Special case for first level
			//For sources
			if (level == 0) {
				currentLevel.add(root);
			}

			//Load parent schedule for the next level
			AstarSchedule childSchedule = currentLevel.get(0);
			AstarSchedule newSchedule = childSchedule.clone();

			//Check if children have become schedulable
			for (AstarTask unshedulable : newSchedule._unschedulable) {
				boolean schedulable = true;
				int taskNum = unshedulable._taskNum;
				for (Integer dependency : partialDependencies.get(taskNum)) {
					if (!newSchedule._scheduled.contains(new AstarTask(dependency))) {
						schedulable = false;
						break;
					}
				}
				if (schedulable) {
					newSchedule._schedulable.add(unshedulable);
				}
			}
			newSchedule._unschedulable.removeAll(newSchedule._schedulable);

			//Determines which node goes first
			List<Integer> DataReadyTimes = new ArrayList<>();
			List<AstarTask> DataReadyTasks = new ArrayList<>();

			//Populate data ready tasks and times
			for (AstarTask schedulable : newSchedule._schedulable) {
				AstarTask newTask = schedulable.clone();
				int drt = Integer.MAX_VALUE;
				for (int processorNum = 0; processorNum < getProcessingCores(); processorNum++) {
					// processor finish time
					int workingDrt = childSchedule._processorLastUsed.get(processorNum);
					for (AstarTask t : newSchedule._scheduled) {
						// if there is a dependency
						if (_arcs[t._taskNum][newTask._taskNum]) {
							int temp = t._start + _nodeWeights[t._taskNum];
							// add the edge weights if switching processors
							if (t._processor != processorNum) {
								temp += _arcWeights[t._taskNum][newTask._taskNum];
							}
							// max of when parents are done and processor is available
							if (temp > workingDrt) {
								workingDrt = temp;
							}
						}
					}
					// assigns node
					if (workingDrt < drt) {
						drt = workingDrt;
						newTask._processor = processorNum;
						newTask._start = workingDrt;
					}
				}
				DataReadyTasks.add(newTask);
				DataReadyTimes.add(drt);
			}

			//Find the lowest DRT of the nodes
			int lowestDRT = Integer.MAX_VALUE;
			int index = 0;
			for (int i = 0; i < DataReadyTimes.size(); i++) {
				if (DataReadyTimes.get(i) < lowestDRT) {
					lowestDRT = DataReadyTimes.get(i);
					index = i;
				}
			}

			//Pick the earliest data ready time task
			AstarTask newTask = DataReadyTasks.get(index);
			//Update schedule
			newSchedule._processorLastUsed.set(newTask._processor, (newTask._start + _nodeWeights[newTask._taskNum]));
			//Move task from schedulable to scheduled
			newSchedule._schedulable.remove(newTask);
			newSchedule._scheduled.add(newTask);
			//Put new sub schedule in parent and the level below
			childSchedule._subSchedules.add(newSchedule);
			nextLevel.add(newSchedule);
		}

		//Return the greedy cost
		return levels.get(levels.size() - 1).get(0).cost();
	}
}