package nz.co.revilo;

import nz.co.revilo.Input.DotFileParser;
import nz.co.revilo.Input.DotFileReader;
import nz.co.revilo.Input.ParseResultListener;
import nz.co.revilo.Output.ScheduleResultListener;
import nz.co.revilo.Scheduling.AlgorithmManager;
import nz.co.revilo.Scheduling.VeryBasicAlgorithmManager;
import org.junit.Before;
import org.junit.Test;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 * Test cases designed for use with the topological sort implementation(s) of AlgorithmManager. These tests only ensure
 * that schedules are valid, NOT optimal. This is done by ensuring all dependencies are satisfied (i.e. arcs correctly
 * acknowledged) in scheduling, and that is no more than one process running on a processor at once.
 */
public class TopologicalTest {
    private AlgorithmManager _algorithmManager;

    /**
     * Sets up a new AlgorithmManager before each test case. AlgorithmManagerImplementations can be switched out
     */
    @Before
    public void setUp() {
        _algorithmManager = new VeryBasicAlgorithmManager(1);
    }

    /**
     * Check that dependencies (i.e. ordering of tasks based on what tasks must be completed for others to start) are
     * correctly being set.
     * @param listener the test result listener containing the information from
     * @return boolean representing if dependencies are satisfied
     */
    public boolean satisfiesDependencies(TestResultListener listener) {
        ArrayList<TestResultListener.Node> nodes = listener.getNodes();
        int nNodes = nodes.size();

        // Iterate through all nodes checking their dependies start at least as late as necessary by node and arc weight
        // constraints.
        for(int i = 0; i < nNodes; i++) {
            TestResultListener.Node currentNode = nodes.get(i);
            List<Integer> dependencies = currentNode.getDependencies();
            List<Integer> arcs = listener.getArcWeights(i);
            int earliestDependencyStart = currentNode.getStartTime() + currentNode.getWeight();
            // Interage through all dependencies, checking if each start time is greater than or equal to the earlier
            // possible
            for(int dependency : dependencies) {
                TestResultListener.Node currentDependency = nodes.get(dependency);
                // If the nodes are on different cores, include processing time
                int earliestCurrentDependencyStart = earliestDependencyStart
                        + (currentDependency.sameCore(currentNode)
                        ? 0 /* if on same core, do not add communication*/
                        : arcs.get(dependency)/* if on different cores, add communication*/ );
                if(currentDependency.getStartTime() < earliestCurrentDependencyStart) {
                    System.out.println("Error: Node " + currentDependency + " starts at time "
                            + currentDependency.getStartTime() + " while Node " + currentNode
                            + " it is dependent on starts at time " + currentNode.getStartTime());
                    return false;
                }
            }
        }
        return true;
    }

}
