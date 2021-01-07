package DaMa_bot;
import battlecode.common.*;

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
        int currInfluence = rc.getInfluence(); //gets current influence
        //bids
        if (currInfluence > 80) {
            if (rc.canBid(currInfluence/2)) {
                rc.bid(currInfluence/2);
            }
        }
        //builds robot
        RobotType toBuild;
        int influence = (int) (currInfluence/2.5);
        if (influence < 30) {
            toBuild  = randomSpawnableRobotType_noPol();
        } else {
            toBuild  = randomSpawnableRobotType();
        }
        for (Direction dir : directions) {
            if (rc.canBuildRobot(toBuild, dir, influence)) {
                rc.buildRobot(toBuild, dir, influence);
            }
        }
    }

    static void runPolitician() throws GameActionException {
        Team enemyTeam = rc.getTeam().opponent();
        Team allyTeam = rc.getTeam();
        int actionRadius = rc.getType().actionRadiusSquared;
        RobotInfo[] attackable = rc.senseNearbyRobots(actionRadius, enemyTeam);

        // Sees a base
        for (RobotInfo robot : attackable) {
            if (robot.type.canBid()){
                if (rc.canEmpower(actionRadius)){
                    rc.empower(actionRadius);
                    return;
                }
            }
        }

        // 2+ enemies around it
        if (attackable.length >= 2 && rc.canEmpower(actionRadius)) {
            System.out.println("empowering...");
            rc.empower(actionRadius);
            System.out.println("empowered");
            return;
        }

        //randomly move
        if (tryMove(randomDirection()))
            System.out.println("I moved!");
    }

    static void runSlanderer() throws GameActionException {
        Team enemyTeam = rc.getTeam().opponent();
        Team allyTeam = rc.getTeam();

        if (tryMove(randomDirection()))
            System.out.println("I moved!");
    }

    static void runMuckraker() throws GameActionException {
        Team enemyTeam = rc.getTeam().opponent();
        Team allyTeam = rc.getTeam();
        int actionRadius = rc.getType().actionRadiusSquared;

        // If there is a slanderer nearby
        for (RobotInfo robot : rc.senseNearbyRobots(actionRadius, enemyTeam)) {
            if (robot.type.canBeExposed()) {
                // It's a slanderer... go get them!
                if (rc.canExpose(robot.location)) {
                    System.out.println("e x p o s e d");
                    rc.expose(robot.location);
                    return;
                }
            }
        }
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
     * Returns a random spawnable RobotType
     *
     * @return a random RobotType
     */
    static RobotType randomSpawnableRobotType() {
        RobotType[] listOfRobots= {RobotType.POLITICIAN, RobotType.POLITICIAN,RobotType.POLITICIAN,
                RobotType.POLITICIAN, RobotType.POLITICIAN,
                RobotType.SLANDERER,RobotType.SLANDERER,
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
