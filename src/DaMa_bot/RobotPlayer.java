package DaMa_bot;
import battlecode.common.*;

import java.util.Map;

public strictfp class RobotPlayer {
    static RobotController rc;

    static final RobotType[] spawnableRobot = {
            RobotType.POLITICIAN,
            RobotType.SLANDERER,
            RobotType.MUCKRAKER,
    };

    static final Direction[] directions = {
            Direction.NORTH,
            Direction.NORTHEAST,
            Direction.EAST,
            Direction.SOUTHEAST,
            Direction.SOUTH,
            Direction.SOUTHWEST,
            Direction.WEST,
            Direction.NORTHWEST,
    };

    static int turnCount;

    /**
     * run() is the method that is called when a robot is instantiated in the Battlecode world.
     * If this method returns, the robot dies!
     **/
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {

        // This is the RobotController object. You use it to perform actions from this robot,
        // and to get information on its current status.
        RobotPlayer.rc = rc;

        turnCount = 0;

        System.out.println("I'm a " + rc.getType() + " and I just got created!");
        while (true) {
            turnCount += 1;
            // Try/catch blocks stop unhandled exceptions, which cause your robot to freeze
            try {
                // Here, we've separated the controls into a different method for each RobotType.
                // You may rewrite this into your own control structure if you wish.
                System.out.println("I'm a " + rc.getType() + "! Location " + rc.getLocation());
                switch (rc.getType()) {
                    case ENLIGHTENMENT_CENTER: runEnlightenmentCenter(); break;
                    case POLITICIAN:           runPolitician();          break;
                    case SLANDERER:            runSlanderer();           break;
                    case MUCKRAKER:            runMuckraker();           break;
                }

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println(rc.getType() + " Exception");
                e.printStackTrace();
            }
        }
    }

    static void runEnlightenmentCenter() throws GameActionException {
        Team enemyTeam = rc.getTeam().opponent();
        Team allyTeam = rc.getTeam();
        int currInfluence = rc.getInfluence(); //gets current influence
        int senseRadius = rc.getType().sensorRadiusSquared;
        RobotInfo[] sensed = rc.senseNearbyRobots(senseRadius, enemyTeam);

        //bids
        if (currInfluence > 100) {
            if (rc.canBid(currInfluence/3)) {
                currInfluence -= currInfluence/3;
                rc.bid(currInfluence/3);
            }
        }

        // Want to conserve some influence
        if (currInfluence <= 50) {
            return;
        }

        //Sense 3+ enemies in vicinity
        if (sensed.length >= 3) {
            for (Direction dir : directions) {
                if (rc.canBuildRobot(RobotType.POLITICIAN, dir, currInfluence/2)) {
                    rc.buildRobot(RobotType.POLITICIAN, dir, currInfluence/2);
                }
            }
        }

        //choose what robot to build
        RobotType toBuild;
        int buildInfluence = (int) (currInfluence/2);
        if (buildInfluence < 35) {
            buildInfluence = buildInfluence/2;
            toBuild  = randomSpawnableRobotType_noPol();
        } else {
            toBuild  = randomSpawnableRobotType();
        }

        //builds robot
        for (Direction dir : directions) {
            if (rc.canBuildRobot(toBuild, dir, buildInfluence)) {
                rc.buildRobot(toBuild, dir, buildInfluence);
            }
        }
    }

    static void runPolitician() throws GameActionException {
        Team enemyTeam = rc.getTeam().opponent();
        Team allyTeam = rc.getTeam();
        int actionRadius = rc.getType().actionRadiusSquared;
        int senseRadius = rc.getType().sensorRadiusSquared;
        RobotInfo[] attackable = rc.senseNearbyRobots(actionRadius, enemyTeam);
        RobotInfo[] sensed = rc.senseNearbyRobots(senseRadius, enemyTeam);
        boolean seeMurk = false;

        // Can attack a base or muckraker
        for (RobotInfo enemy : attackable) {
            if (enemy.type.canBid()){
                if (rc.canEmpower(actionRadius)){
                    rc.empower(actionRadius);
                    return;
                }
            }
            // finds a muckraker
            if (enemy.type.canExpose()) {
                seeMurk = true;
            }
        }

        // Move toward a sensed enemy base
        for (RobotInfo enemy : sensed) {
            if (enemy.getType().canBid()){
                MapLocation currentloc = rc.getLocation();
                Direction dirMove = currentloc.directionTo(enemy.getLocation());
                if (rc.canMove(dirMove)) {
                    rc.move(dirMove);
                    return;
                }
            }
        }

        // If we saw a murk, empower w 50% chance
        if (seeMurk == true && Math.random() >= .5){
            if (rc.canEmpower(actionRadius)){
                rc.empower(actionRadius);
                return;
            }
        }

        // 3+ enemies around it
        if (attackable.length >= 3 && rc.canEmpower(actionRadius)) {
            System.out.println("empowering...");
            rc.empower(actionRadius);
            System.out.println("empowered");
            return;
        }

        //Move towards groups of enemies
        if (sensed.length >= 3) {
            for (RobotInfo enemy : sensed) {
                // move toward one of them
                MapLocation currentloc = rc.getLocation();
                Direction dirMove = currentloc.directionTo(enemy.getLocation());
                if (rc.canMove(dirMove)) {
                    rc.move(dirMove);
                    return;
                }
            }
        }

        //Move w certain chance in best direction w largest passability
        if (tryMove(findBestDirection())) {
            System.out.println("I moved!");
            return;
        }
        //randomly move
        if (tryMove(randomDirection()))
            System.out.println("I moved!");
    }

    static void runSlanderer() throws GameActionException {
        Team enemyTeam = rc.getTeam().opponent();
        Team allyTeam = rc.getTeam();
        int senseRadius = rc.getType().sensorRadiusSquared;
        RobotInfo[] sensed = rc.senseNearbyRobots(senseRadius, enemyTeam);

        //Move away from mucks or pols
        for (RobotInfo enemy : sensed) {
            // move away from a muck or pol
            if (enemy.getType().canEmpower() || enemy.type.canExpose()){
                MapLocation currentloc = rc.getLocation();
                Direction dirMove = currentloc.directionTo(enemy.getLocation()).opposite();
                if (rc.canMove(dirMove)) {
                    rc.move(dirMove);
                    return;
                }
            }
        }

        //Move w certain chance in best direction w largest passability
        if (tryMove(findBestDirection())) {
            System.out.println("I moved!");
            return;
        }

        //Move randomly
        if (tryMove(randomDirection()))
            System.out.println("I moved!");
    }

    static void runMuckraker() throws GameActionException {
        Team enemyTeam = rc.getTeam().opponent();
        Team allyTeam = rc.getTeam();
        int actionRadius = rc.getType().actionRadiusSquared;
        int senseRadius = rc.getType().sensorRadiusSquared;
        int detectRadius = rc.getType().detectionRadiusSquared;

        RobotInfo[] attackable = rc.senseNearbyRobots(actionRadius, enemyTeam);
        RobotInfo[] sensed = rc.senseNearbyRobots(senseRadius, enemyTeam);
        RobotInfo[] detected = rc.senseNearbyRobots(detectRadius, enemyTeam);

        // If there is a slanderer nearby
        for (RobotInfo enemy : attackable) {
            if (enemy.type.canBeExposed()) {
                // It's a slanderer... go get them!
                if (rc.canExpose(enemy.location)) {
                    System.out.println("e x p o s e d");
                    rc.expose(enemy.location);
                    return;
                }
            }
        }

        //If we sense a slanderer
        for (RobotInfo enemy : sensed) {
            if (enemy.type.canBeExposed()){
                // move toward a slanderer
                MapLocation currentloc = rc.getLocation();
                Direction dirMove = currentloc.directionTo(enemy.getLocation());
                if (rc.canMove(dirMove)) {
                    rc.move(dirMove);
                    return;
                }
            }
        }

        //Move towards groups of enemies
        if (sensed.length >= 3) {
            for (RobotInfo enemy : sensed) {
                // move toward one of them
                MapLocation currentloc = rc.getLocation();
                Direction dirMove = currentloc.directionTo(enemy.getLocation());
                if (rc.canMove(dirMove)) {
                    rc.move(dirMove);
                    return;
                }
            }
        }

        //Move w certain chance in best direction w largest passability
        if (tryMove(findBestDirection())) {
            System.out.println("I moved!");
            return;
        }

        //Move randomly
        if (tryMove(randomDirection()))
            System.out.println("I moved!");
    }

    /**
     * Returns a random Direction.
     *
     * @return a random Direction
     */
    static Direction randomDirection() {
        return directions[(int) (Math.random() * directions.length)];
    }

    /**
     * Returns available direction with highest passability. Only 60% chance to actually return that direction
     *
     * @return best direction
     */
    static Direction findBestDirection() throws GameActionException {
        double maxPassability = 0.1;
        Direction bestDir = Direction.NORTH;
        MapLocation current = rc.getLocation();
        for (Direction dir : directions) {
            MapLocation possibleMove = current.add(dir);
            if (rc.canSenseLocation(possibleMove)) {
                double possiblePassability = rc.sensePassability(possibleMove);
                if (possiblePassability > maxPassability && Math.random() > .40) {
                    maxPassability = possiblePassability;
                    bestDir = dir;
                }
            }
        }
        return bestDir;
    }

    /**
     * Returns a random spawnable RobotType
     *
     * @return a random RobotType
     */
    static RobotType randomSpawnableRobotType() {
        RobotType[] listOfRobots= {RobotType.POLITICIAN, RobotType.POLITICIAN,RobotType.POLITICIAN,
                RobotType.POLITICIAN, RobotType.POLITICIAN,
                RobotType.SLANDERER,RobotType.SLANDERER,
                RobotType.MUCKRAKER,RobotType.MUCKRAKER,};
        return listOfRobots[(int) (Math.random() * listOfRobots.length)];
    }

    static RobotType randomSpawnableRobotType_noPol() {
        RobotType[] listOfRobots= {
                RobotType.SLANDERER,RobotType.SLANDERER,
                RobotType.SLANDERER,RobotType.SLANDERER,
                RobotType.MUCKRAKER,RobotType.MUCKRAKER,};
        return listOfRobots[(int) (Math.random() * listOfRobots.length)];
    }

    /**
     * Attempts to move in a given direction.
     *
     * @param dir The intended direction of movement
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryMove(Direction dir) throws GameActionException {
        System.out.println("I am trying to move " + dir + "; " + rc.isReady() + " " + rc.getCooldownTurns() + " " + rc.canMove(dir));
        if (rc.canMove(dir)) {
            rc.move(dir);
            return true;
        } else return false;
    }
}
