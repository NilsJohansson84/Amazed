package amazed.solver;

import amazed.maze.Maze;
import amazed.maze.Player;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * <code>ForkJoinSolver</code> implements a solver for
 * <code>Maze</code> objects using a fork/join multi-thread
 * depth-first search.
 * <p>
 * Instances of <code>ForkJoinSolver</code> should be run by a
 * <code>ForkJoinPool</code> object.
 */


public class ForkJoinSolver
    extends SequentialSolver
{
    /**
     * Creates a solver that searches in <code>maze</code> from the
     * start node to a goal.
     *
     * @param maze   the maze to be searched
     */

    AtomicReference<List<Integer>> solution;
    Set<Integer> claimed;

    //int player;

    public ForkJoinSolver(Maze maze)
    {
        super(maze);
        this.visited = new ConcurrentSkipListSet<>();
        this.predecessor = new ConcurrentHashMap<>();
        this.solution = new AtomicReference<>(null);
        this.claimed = new ConcurrentSkipListSet<>(); // Maybe ConcurrentHashMap.newKeySet();
    }

    /**
     * Creates a solver that searches in <code>maze</code> from the
     * start node to a goal, forking after a given number of visited
     * nodes.
     *
     * @param maze        the maze to be searched
     * @param forkAfter   the number of steps (visited nodes) after
     *                    which a parallel task is forked; if
     *                    <code>forkAfter &lt;= 0</code> the solver never
     *                    forks new tasks
     */
    public ForkJoinSolver(Maze maze, int forkAfter)
    {
        super(maze);
        this.forkAfter = forkAfter;
        this.visited = new ConcurrentSkipListSet<>();
        this.predecessor = new ConcurrentHashMap<>();
        this.solution = new AtomicReference<>(null);
        this.claimed = new ConcurrentSkipListSet<>();

    }

    /*
    public ForkJoinSolver(Maze maze, ConcurrentSkipListSet<Integer> visited, ConcurrentHashMap<Integer, Integer> predecessor,
                          AtomicReference<List<Integer>> solution, ConcurrentSkipListSet<Integer> claimed)
    {
        super(maze);
        this.visited = new ConcurrentSkipListSet<>();
        this.predecessor = new ConcurrentHashMap<>();
        this.solution = new AtomicReference<>(null);
        this.claimed = new ConcurrentSkipListSet<>();

    }
     */

    /**
     * Searches for and returns the path, as a list of node
     * identifiers, that goes from the start node to a goal node in
     * the maze. If such a path cannot be found (because there are no
     * goals, or all goals are unreacheable), the method returns
     * <code>null</code>.
     *
     * @return   the list of node identifiers from the start node to a
     *           goal node in the maze; <code>null</code> if such a path cannot
     *           be found.
     */
    @Override
    public List<Integer> compute()
    {
        return parallelSearch();
    }

    private List<Integer> parallelSearch() {

        if (solution.get() != null){
            return solution.get();
        }

        List<ForkJoinSolver> children = new ArrayList<>();
        final int originalStart = maze.start(); // Defines maze start

        // Create a player for thread, forks also get player
        int player = maze.newPlayer(start);

        frontier.push(start);
        claimed.add(start);

        //Check frontier
        while (!frontier.isEmpty()) {

            // If solution found, end
            if (solution.get() != null){
                return solution.get();
            }

            //Only add to visited right before moving
            int current = frontier.pop();

            if (!visited.add(current)){
                continue;
            } else maze.move(player, current);



            // Goal found, pathFromTo from originalStart to current node
            if (maze.hasGoal(current)) {
                List<Integer> solved = pathFromTo(originalStart, current);
                solution.compareAndSet(null, solved);
                return solved;
            }


            //Add all available neighbors to "claimed", reserves them for visiting
            List<Integer> unvisitedNeighbors = new ArrayList<>();

            for (int nb : maze.neighbors(current)) {
                if (claimed.add(nb)) {
                    predecessor.putIfAbsent(nb, current);
                    unvisitedNeighbors.add(nb);
                }
            }

            // If unvisited neighbors is empty, continue
            if (unvisitedNeighbors.isEmpty()) {
                continue;
            }

            //First neighbor, business as usual
            frontier.push(unvisitedNeighbors.get(0));

            // More than one neighbor, copy parent info to child, fork and continue parent search
            for (int i = 1; i < unvisitedNeighbors.size(); i++) {

                int nb = unvisitedNeighbors.get(i);

                ForkJoinSolver child = new ForkJoinSolver(maze);

                // Copy DFS state into child
                children.add(child);

                child.start = nb;
                child.visited = this.visited;
                child.predecessor = new ConcurrentHashMap<>(predecessor);
                child.solution = this.solution;
                child.claimed = this.claimed;

                child.frontier.push(nb);
                child.fork();
            }
        }

        // When exiting, join children and return if result != null
        for (ForkJoinSolver f : children) {
            List<Integer> result = f.join();

            if (result != null) {
                return result;
            }
        }
        //Return if frontier = 0
        return solution.get();
    }

}