package amazed.solver;

import amazed.maze.Maze;
import amazed.maze.Player;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
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

    public ForkJoinSolver(Maze maze)
    {
        super(maze);
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
        this(maze);
        this.forkAfter = forkAfter;
    }

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

        //if (visited.contains(start)){
          //  return null;
        //}

        List<ForkJoinSolver> children = new ArrayList<>();
        final int originalStart = maze.start(); // Defines maze start

        // Maybe atomic something

        // Create a player for thread, forks also get player
        int player = maze.newPlayer(start);

        frontier.push(start);

        while (!frontier.isEmpty()) {

            int current = frontier.pop();

            maze.move(player,current);

            // Goal found, pathFromTo from originalStart to current node
            if (maze.hasGoal(current)) {
                return pathFromTo(originalStart, current);
            }

            List<Integer> unvisitedNeighbors = new ArrayList<>();

            /*
            for (int nb : maze.neighbors(current)) {
                if (visited.add(nb)) {
                    unvisitedNeighbors.add(nb);
                    predecessor.putIfAbsent(nb, current);
                    System.out.println(nb + "is unvisited");
                }
            }

             */

            for (int nb : maze.neighbors(current)) {
                if (!visited.add(nb)) {
                    continue;
                }
                    unvisitedNeighbors.add(nb);
                    predecessor.putIfAbsent(nb, current);
                    System.out.println(nb + "is unvisited");
            }



            visited.add(start);

            // If unvisited neighbors is empty, continue
            if (unvisitedNeighbors.isEmpty()) {
                continue;
            }

            frontier.push(unvisitedNeighbors.get(0));

            // More than one neighbor, copy parent info to child, fork and continue parent search
            for (int i = 1; i < unvisitedNeighbors.size(); i++) {

                int nb = unvisitedNeighbors.get(i);

                ForkJoinSolver child = new ForkJoinSolver(maze);

                // Copy DFS state into child
                child.start = nb;
                child.visited = this.visited;
                child.predecessor = new HashMap<>(predecessor);

                child.fork();
                children.add(child);
            }

            // When exiting, join children and return if result != null
            for (ForkJoinSolver f : children) {
                if (f.isDone()) {
                    List<Integer> result = f.join();
                    if (result != null) {
                        return result;
                    }
                }
            }
        }

        // When exiting, join children and return if result != null
        for (ForkJoinSolver f : children) {
            List<Integer> result = f.join();

            if (result != null) {
                return result;
            }
        }

        return null;
    }

    }


    // Set<Integer> childVisits = f.visited;
//                visited.addAll(childVisits);