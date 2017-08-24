package nz.co.revilo.Scheduling;

import nz.co.revilo.Output.ScheduleResultListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Finds optimal schedule using DFS Branch and Bound
 *
 * @author Abby S, Michael K (optimiser, cleaner and documentor)
 *
 */
public class BranchAndBoundAlgorithmManager extends AlgorithmManager {

	List<Integer> sources=new ArrayList<>();
	private List<Integer> bottomUpSinks=new ArrayList<>();
	private List<Schedule> rootSchedules=new ArrayList<>();
	int[] bottomLevels;
	int numNodes;
	int totalNodeWeights;
//	private int upperBound; //parent has this instead
	private Schedule optimalSchedule;
	private List<Integer> nodeStartTimes=new ArrayList<>();
	private List<Integer> nodeProcessors=new ArrayList<>();
	private List<String> existingScheduleStructures=new ArrayList<>();

	public BranchAndBoundAlgorithmManager(int processingCores) {
		super(processingCores);
	}
	@Override
	protected void execute(){
		numNodes=_nodeWeights.length;
		bottomLevels=new int[numNodes];
		NeighbourManagerHelper.setUpHelper(numNodes, _arcs);

		//get sources
		for(int nodeId=0; nodeId<numNodes; nodeId++){
			//check that sources have no parents
			if (!NeighbourManagerHelper.hasInneighbours(nodeId)) {
				//if they don't have parents, then add it to a sources queue
				sources.add(nodeId);
				//start a schedule with this node as source on each possible processor
			}
			//sinks
			else if(!NeighbourManagerHelper.hasOutneighbours(nodeId)){
				bottomUpSinks.add(nodeId);
				bottomLevels[nodeId]=_nodeWeights[nodeId];
			}

			totalNodeWeights+=_nodeWeights[nodeId];
		}

        /*
         * Definitely have sources as a row at start of each processor if there aren't more sources than cores
		 * All others will just be permutations
		 * If stack sources on same processor, will be less optimal
		 */
        int processor = 0;
        for (int nodeId : sources) {
            Schedule newSchedule = new Schedule(this, null, nodeId, processor);
            rootSchedules.add(newSchedule);
		}

		upperBound=totalNodeWeights + 1; //TODO: is this a good upper bound?
        //Take a greedy path down the tree to find a more realistic upper bound
        upperBound = greedyUpperBound();

		calculateBottomLevels();

		while(!rootSchedules.isEmpty()){
			bnb(rootSchedules.remove(0));
		}

		returnResults();
	}



	/**
	 * Return the optimal schedule found and it's information
	 * 
	 * @author Abby S
	 * 
	 */
	private void returnResults() {
		for(int nodeId=0; nodeId<numNodes; nodeId++){
			nodeStartTimes.add(optimalSchedule.closedNodes.get(nodeId).getA());//start times
			nodeProcessors.add(optimalSchedule.closedNodes.get(nodeId).getB());//processors scheduled on
		}	
		System.out.println("Optimal length found: "+optimalSchedule.getMaxFinishTime());

		//pass to outputs
		for (ScheduleResultListener listener: getListeners()) {
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
	 * 
	 * @author Abby S, Terran K
	 */
	private void bnb(Schedule schedule) {
		//TODO: not strict enough?
        if (schedule.lowerBound >= upperBound) { //>= @ Michael K, huge optimisation
            schedule=null; //garbage collect that schedule
			brokenTrees++; //this tree has broken
			return; //break tree at this point
		}

		//compare to existing schedule structures and remove if duplicate
		if(existingScheduleStructures.contains(schedule._scheduleStructureId)){
			schedule=null; //garbage collect that schedule
			brokenTrees++; // this tree has broken
			return; //break tree at this point
		} else {
			existingScheduleStructures.add(schedule._scheduleStructureId);
		}

		//found optimal for the root started with
		//reached end of a valid schedule. Never broke off, so is optimal
		if(schedule.openNodes.isEmpty()){
			//TODO: doing this to make sure only optimal schedules get through
			if(schedule.getMaxFinishTime()<upperBound){
				optimalSchedule=schedule;
				// if OptimalListener is null it means that we're not actually asking for updates
				// because we are likely not using a visualization
				if (getOptimalListener() != null) {
					getOptimalListener().newOptimal(optimalSchedule);
				}
				upperBound=schedule.getMaxFinishTime();
				System.out.println(upperBound);
				return;
			}
		}

		//continue DFS
		List<Schedule> nextSchedules = new ArrayList<>();
		for(int node:schedule.independentNodes){
			for(int processor=0; processor<_processingCores; processor++){
				nextSchedules.add(new Schedule(this, schedule, node, processor));
			}
		}
		for(Schedule nextSchedule:nextSchedules){
			bnb(nextSchedule);
		}
	}

	/**
	 * Calculates bottom level of each node in the graph
	 * Using bottom-up approach
	 * 
	 * @author Abby S
	 * 
	 */
	private void calculateBottomLevels() {
		while(!bottomUpSinks.isEmpty()){
			int nodeId = bottomUpSinks.remove(0);
			List<Integer> inneighbours=NeighbourManagerHelper.getInneighbours(nodeId);

			for(int inneighbour:inneighbours){
				//bottom up add its weight to child's
				int fromGivenNode=bottomLevels[nodeId]+_nodeWeights[inneighbour];
				//Farthest distance needed from bottom
				bottomLevels[inneighbour]=bottomLevels[inneighbour]>fromGivenNode?bottomLevels[inneighbour]:fromGivenNode;

				//inneighbours.remove(inneighbour); //ordered access so don't actually need to remove
				List<Integer> inneighboursChildren=NeighbourManagerHelper.getOutneighbours(inneighbour); //nodes with 1 on the node's row
				inneighboursChildren.remove(Integer.valueOf(nodeId)); //Integer or will treat the int as index
				if(inneighboursChildren.isEmpty()){
					bottomUpSinks.add(inneighbour);//become a sink now that child is removed
				}
			}
		}	
	}

    /**
     * Calculates an upper bound using a greedy algorithm
     *
     * @return an upper bound
     */
    private int greedyUpperBound() {

        int numTasks;
        private List<Set<Integer>> completeDependencies;

    public AstarFirstAlgorithmManager( int processingCores){
            super(processingCores);
        }

        @Override
        protected void execute () {
            //Number of Tasks
            numTasks = _nodeWeights.length;

            //Sort Tasks into start, middle and end sets
            Set<Integer> startTasks = new HashSet<>();
            Set<Integer> endTasks = new HashSet<>();
            Set<Integer> middleTasks = new HashSet<>();
            for (int task = 0; task < numTasks; task++) {
                startTasks.add(task);
                endTasks.add(task);
                middleTasks.add(task);
            }
            for (int fromTask = 0; fromTask < numTasks; fromTask++) {
                for (int toTask = 0; toTask < numTasks; toTask++) {
                    if (_arcs[fromTask][toTask]) {
                        startTasks.remove(toTask);
                        endTasks.remove(fromTask);
                    }
                }
            }
            middleTasks.removeAll(endTasks);
            middleTasks.removeAll(startTasks);

            //Determine total completeDependencies for every task
            completeDependencies = new ArrayList<>(numTasks);
            for (int task = 0; task < numTasks; task++) {
                completeDependencies.add(new HashSet<>());
            }
            for (Integer startTask : startTasks) {
                updateDependencies(new ArrayList<>(), startTask);
            }

            //Determine partial dependencies for every task
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
            Schedule root = new Schedule();
            for (int unschedulableTask : middleTasks) {
                root._unschedulable.add(new Task(unschedulableTask));
            }
            for (int unschedulableTask : endTasks) {
                root._unschedulable.add(new Task(unschedulableTask));
            }
            for (int schedulableTask : startTasks) {
                root._schedulable.add(new Task(schedulableTask));
            }
            //Sets when the processors were last used
            root._processorLastUsed = new ArrayList<>(_processingCores);
            for (int processor = 0; processor < _processingCores; processor++) {
                root._processorLastUsed.add(0);
            }

            //Iterative BFS
            //Initialise each level
            List<List<Schedule>> levels = new ArrayList<>(numTasks + 1);
            for (int level = 0; level <= numTasks; level++) {
                levels.add(new ArrayList<>());
            }
            //Calculate
            for (int level = 0; level < numTasks; level++) {
                //Load current and next level
                List<Schedule> currentLevel = levels.get(level);
                List<Schedule> nextLevel = levels.get(level + 1);
                //Special case for first level
                if (level == 0) {
                    levels.get(0).add(root);
                }
                //For every schedule in the level
                for (int child = 0; child < currentLevel.size(); child++) {
                    //Load it
                    Schedule childSchedule = currentLevel.get(child);
                    //Then for every processor
                    for (int processorNum = 0; processorNum < _processingCores; processorNum++) {
                        //And for every task that can be scheduled
                        for (Task task : childSchedule._schedulable) {
                            //Clone
                            Schedule newSchedule = childSchedule.clone();
                            Task newTask = task.clone();

                            //Set processor
                            newTask._processor = processorNum;

                            //Determine data ready time
                            int drt = childSchedule._processorLastUsed.get(processorNum);
                            for (Task t : childSchedule._scheduled) {
                                if (_arcs[t._taskNum][newTask._taskNum]) {
                                    int temp = t._start + _nodeWeights[t._taskNum];
                                    if (t._processor != processorNum) {
                                        temp += _arcWeights[t._taskNum][newTask._taskNum];
                                    }
                                    if (drt < temp) {
                                        drt = temp;
                                    }
                                }
                            }

                            newTask._start = drt;

                            newSchedule._processorLastUsed.set(processorNum, (drt + _nodeWeights[newTask._taskNum]));

                            //Move task to scheduled from schedulable
                            newSchedule._schedulable.remove(newTask);
                            newSchedule._scheduled.add(newTask);

                            //Check if unschedulables are schedulable
                            for (Task unshedulable : newSchedule._unschedulable) {
                                boolean schedulable = true;
                                int taskNum = unshedulable._taskNum;
                                for (Integer dependency : partialDependencies.get(taskNum)) {
                                    if (!newSchedule._scheduled.contains(new Task(dependency))) {
                                        schedulable = false;
                                        break;
                                    }
                                }
                                if (schedulable) {
                                    newSchedule._schedulable.add(unshedulable);
                                }
                            }
                            newSchedule._unschedulable.removeAll(newSchedule._schedulable);

                            //Put new sub schedule in parent and the level below
                            childSchedule._subSchedules.add(newSchedule);
                            nextLevel.add(newSchedule);
                        }
                    }
                }
            }

            //Find the lowest cost schedule
            int temp = levels.get(levels.size() - 1).get(0).cost();
            Schedule best = levels.get(levels.size() - 1).get(0);
            for (Schedule s : levels.get(levels.size() - 1)) {
                if (s.cost() < temp) {
                    temp = s.cost();
                    best = s;
                }
            }
        }
}