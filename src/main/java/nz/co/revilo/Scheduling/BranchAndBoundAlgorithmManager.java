package nz.co.revilo.Scheduling;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Finds optimal schedule using DFS Branch and Bound
 * 
 * @author Abby S
 *
 */
public class BranchAndBoundAlgorithmManager extends AlgorithmManager {

	List<Integer> sources=new ArrayList<>();
	private List<Integer> bottomUpSinks=new ArrayList<>();
	private List<Schedule> rootSchedules=new ArrayList<>();
	int[] bottomLevels;
	int numNodes;
	int totalNodeWeights;
	private AtomicInteger upperBound = new AtomicInteger();
	private Schedule optimalSchedule;
	private List<Integer> nodeStartTimes=new ArrayList<>();
	private List<Integer> nodeProcessors=new ArrayList<>();
	private Set<Integer> existingScheduleStructures= Collections.synchronizedSet(new HashSet<>());

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
		if(sources.size()<=_processingCores){
			int processor=0;
			for(int nodeId : sources) {
				Schedule newSchedule = new Schedule(this, null, nodeId, processor);
				rootSchedules.add(newSchedule);
				processor++;
			}
		} else {
			for(int nodeId : sources) {
				for(int processor=0; processor<_processingCores; processor++){
					Schedule newSchedule = new Schedule(this, null, nodeId, processor);
					rootSchedules.add(newSchedule);
				}
			}
		}

		upperBound.set(totalNodeWeights + 1); //TODO: is this a good upper bound?
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

		//pass to output
		getListener().finalSchedule(
				_graphName,
				Arrays.asList(_nodeNames),
				PrimitiveInterfaceHelper.primToBoolean2D(_arcs),
				PrimitiveInterfaceHelper.primToInteger2D(_arcWeights),
				PrimitiveInterfaceHelper.primToInteger1D(_nodeWeights),
				nodeStartTimes,
				nodeProcessors
				);
	}

	/**
	 * bnb based on the current schedule s
	 * 
	 * @param schedule
	 * 
	 * @author Abby S
	 */
	private void bnb(final Schedule schedule) {
		//TODO: not strict enough?
		if(schedule.lowerBound>=upperBound.get()){
			//schedule=null; //garbage collect that schedule
			return; //break tree at this point
		}

		//compare to existing schedule structures and remove if duplicate
		synchronized (existingScheduleStructures){
			if(existingScheduleStructures.contains(schedule._scheduleStructureId)){
				//schedule=null; //garbage collect that schedule
				return; //break tree at this point
			} else {
				existingScheduleStructures.add(schedule._scheduleStructureId);
			}
		}

		//found optimal for the root started with
		//reached end of a valid schedule. Never broke off, so is optimal
		if(schedule.openNodes.isEmpty()){			
			//TODO: doing this to make sure only optimal schedules get through
			final int maxFinishTime = schedule.getMaxFinishTime();
			upperBound.getAndUpdate((upper)-> {
				if(maxFinishTime <= upper){
					optimalSchedule = schedule;
					return maxFinishTime;
				}else{
					return upper;
				}
			});
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
				//bottom up add it's weight to child's
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
}