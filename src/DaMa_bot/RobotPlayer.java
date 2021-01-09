package DaMa_bot;
import battlecode.common.*;
import java.util.*;

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
    static MapLocation enemyBaseLoc = new MapLocation(0, 0);
    static int homeID;
    static MapLocation homeLoc;

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
        RobotInfo[] sensedAllies = rc.senseNearbyRobots(senseRadius, allyTeam);

        //bids
        if (currInfluence > 90) {
            if (rc.canBid(currInfluence/3)) {
                currInfluence -= currInfluence/3;
                rc.bid(currInfluence/3);
            }
        } else {
            if (rc.canBid(2) && Math.random() > .6) {
                currInfluence -= 2;
                rc.bid(2);
            }
        }

        // Want to conserve some influence or too many friendly units around
        if (currInfluence <= 50 || sensedAllies.length > 35) {
            return;
        }

        //Sense 3+ enemies in vicinity, so build politicians to kill them
        if (sensed.length >= 3) {
            for (Direction dir : directions) {
                if (rc.canBuildRobot(RobotType.POLITICIAN, dir, currInfluence/2)) {
                    rc.buildRobot(RobotType.POLITICIAN, dir, currInfluence/2);
                    return;
                }
            }
        }

        //choose what robot to build
        RobotType toBuild;
        boolean buildBool;
        int buildInfluence = (int) (currInfluence/2);
        double polPercent;

        if (turnCount < 400) {
            polPercent = .75;
        } else {
            polPercent = .5;
        }

        if (buildInfluence < 25) {
            buildBool = Math.random() > .4;
            buildInfluence /= 2;
            toBuild = RobotType.MUCKRAKER;
        } else {
            buildBool = Math.random() > .3;
            double percent = Math.random();
            if (percent > polPercent) {
                toBuild = RobotType.POLITICIAN;
            } else if (percent > .2) {
                buildInfluence = buildInfluence - buildInfluence % 20 + 1;
                toBuild = RobotType.SLANDERER;
            } else {
                buildInfluence /= 2;
                toBuild = RobotType.MUCKRAKER;
            }
        }

        //builds robot in random direction
        if (buildBool) {
            List<Direction> shuffledDir =  Arrays.asList(directions);
            Collections.shuffle(shuffledDir);
            for (Direction dir : shuffledDir) {
                if (rc.canBuildRobot(toBuild, dir, buildInfluence)) {
                    System.out.println("I made a "+ toBuild + ".");
                    rc.buildRobot(toBuild, dir, buildInfluence);
                    return;
                }
            }
        }
    }

    static void runPolitician() throws GameActionException {
        Team enemyTeam = rc.getTeam().opponent();
        Team allyTeam = rc.getTeam();
        MapLocation currentloc = rc.getLocation();
        int actionRadius = rc.getType().actionRadiusSquared;
        int senseRadius = rc.getType().sensorRadiusSquared;
        RobotInfo[] attackable = rc.senseNearbyRobots(actionRadius, enemyTeam);
        RobotInfo[] attackableNeutral = rc.senseNearbyRobots(actionRadius, Team.NEUTRAL);
        RobotInfo[] sensed = rc.senseNearbyRobots(senseRadius, enemyTeam);
        RobotInfo[] sensedNeutral = rc.senseNearbyRobots(senseRadius, Team.NEUTRAL);
        RobotInfo[] sensedAlly = rc.senseNearbyRobots(senseRadius, allyTeam);
        RobotInfo[] closeSensedAlly = rc.senseNearbyRobots(2, allyTeam);

        boolean seeMurk = false;

        if (turnCount <= 13) {
            for (RobotInfo ally :closeSensedAlly) {
                if (ally.getType().canBid()){
                    homeID = ally.getID();
                    homeLoc = ally.getLocation();
                }
            }
        }


        // Can attack an enemy base or muckraker
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
                MapLocation enemyLoc = enemy.getLocation();
                int flagNum = codeFlag(enemyLoc, 0); // Set a flag for enemy base location
                if (trySetFlag(flagNum)){
                    System.out.println("Set Flag " + flagNum);
                }

                Direction dirMove = currentloc.directionTo(enemyLoc);
                if (rc.canMove(dirMove)) {
                    rc.move(dirMove);
                    return;
                }
            }
        }

        // Attack neutral base if < 100 influence
        for (RobotInfo neutral : attackableNeutral) {
            if (neutral.type.canBid() && neutral.getInfluence() <= 100){
                if (rc.canEmpower(actionRadius)){
                    rc.empower(actionRadius);
                    return;
                }
            }
            if (neutral.type.canBid() && Math.random() > .6){
                if (rc.canEmpower(actionRadius)){
                    rc.empower(actionRadius);
                    return;
                }
            }
        }

        // Move toward a neutral base if < 100 influence
        for (RobotInfo neutral : sensedNeutral) {
            if (neutral.getType().canBid() && neutral.getInfluence() <= 100){
                Direction dirMove = currentloc.directionTo(neutral.getLocation());
                if (rc.canMove(dirMove)) {
                    rc.move(dirMove);
                    return;
                }
            }
            if (neutral.getType().canBid() && Math.random() > .3){
                Direction dirMove = currentloc.directionTo(neutral.getLocation());
                if (rc.canMove(dirMove)) {
                    rc.move(dirMove);
                    return;
                }
            }
        }

        // Move toward enemyBase if not (0,0)
        if (enemyBaseLoc.x != 0 && enemyBaseLoc.y != 0) {
            Direction dirMove = currentloc.directionTo(enemyBaseLoc);
            if (rc.canMove(dirMove)) {
                rc.move(dirMove);
                return;
            }
        }

        //Decode neighboring flags
        for (RobotInfo ally : sensedAlly) {
             int allyFlag = rc.getFlag(ally.getID());
             if (allyFlag > 1) {
                enemyBaseLoc = decodeFlag(allyFlag);
                System.out.println("Decoded a flag with location: " + enemyBaseLoc);
                trySetFlag(allyFlag); //Set own Flag as the same
                break;
             }
        }

        // If we saw a murk, empower w 40% chance
        if (seeMurk && Math.random() >= .4){
            if (rc.canEmpower(actionRadius)){
                rc.empower(actionRadius);
                return;
            }
        }

        // Empower if 3+ enemies around it
        if (attackable.length >= 3 && rc.canEmpower(actionRadius)) {
            System.out.println("empowering...");
            rc.empower(actionRadius);
            System.out.println("empowered");
            return;
        }

        //Move towards groups of 2 enemies
        if (sensed.length >= 2) {
            for (RobotInfo enemy : sensed) {
                // move toward one of them
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
    }

    static void runSlanderer() throws GameActionException {
        Team enemyTeam = rc.getTeam().opponent();
        Team allyTeam = rc.getTeam();
        int senseRadius = rc.getType().sensorRadiusSquared;
        RobotInfo[] sensed = rc.senseNearbyRobots(senseRadius, enemyTeam);

        //Move away from mucks or pols
        for (RobotInfo enemy : sensed) {
            // move away from a muck or pol
            if (enemy.getType().canEmpower() || enemy.getType().canExpose()){
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
    }

    static void runMuckraker() throws GameActionException {
        Team enemyTeam = rc.getTeam().opponent();
        Team allyTeam = rc.getTeam();
        MapLocation currentloc = rc.getLocation();
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

        //If we sense a slanderer or enemy base
        for (RobotInfo enemy : sensed) {
            //Sense slanderer
            if (enemy.getType().canBeExposed()) {
                Direction dirMove = currentloc.directionTo(enemy.getLocation());
                if (rc.canMove(dirMove)) { // move toward a slanderer
                    rc.move(dirMove);
                    return;
                }
            }
            //Sense enemy base
            if (enemy.getType().canBid()) {
                MapLocation enemyLoc = enemy.getLocation();
                int flagNum = codeFlag(enemyLoc, 0); // Set a flag for enemy base location
                if (trySetFlag(flagNum)) {
                    System.out.println("Set Flag: " + flagNum);
                }
            }
        }

        //Move towards groups of enemies
        if (sensed.length >= 2) {
            for (RobotInfo enemy : sensed) {
                // move toward one of them
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
    }

    /**
     * Returns available direction with highest passability.
     * Can choose random direction with 10%.
     * Only 50% chance to actually return that direction
     *
     * @return best direction
     */
    static Direction findBestDirection() throws GameActionException {
        double maxPassability = 0.1;
        MapLocation current = rc.getLocation();
        List<Direction> shuffledDir =  Arrays.asList(directions);
        Collections.shuffle(shuffledDir);
        Direction bestDir = shuffledDir.get(0);

        if (Math.random() > .10) { // 10% chance its random
            for (Direction dir : shuffledDir) {
                MapLocation possibleMove = current.add(dir);
                if (rc.canSenseLocation(possibleMove)) {
                    double possiblePassability = rc.sensePassability(possibleMove);
                    if (possiblePassability > maxPassability && Math.random() > .40 && rc.canMove(dir)) {
                        maxPassability = possiblePassability;
                        bestDir = dir;
                    }
                }
            }
        }
        return bestDir;
    }

    /**
     * Attempts to set a flag with given int.
     *
     * @return boolean
     */
    static boolean trySetFlag(int flagNum) throws GameActionException {
        System.out.println("I am trying to set flag " + flagNum + ".");
        if (rc.canSetFlag(flagNum)) {
            rc.setFlag(flagNum);
            return true;
        } else return false;
    }

    /**
     * Attempts to code a flag given a MapLocation
     * 128 * (x % 128) + (y % 128)
     *
     * @return int
     */
    static int codeFlag(MapLocation baseLoc, int extraInfo) throws GameActionException {
        return 128*(baseLoc.x % 128) + baseLoc.y % 128 + extraInfo * 128 * 128;
    }

    /**
     * Attempts to decode a flag given a flag int
     *
     * @return MapLocation
     */
    static MapLocation decodeFlag(int flag) throws GameActionException {
        int y = flag % 128;
        int x = (flag/128) % 128;
        int extrainfo = flag / 128 / 128;

        MapLocation currentLocation = rc.getLocation();
        int offsetX128 = currentLocation.x / 128;
        int offsetY128 = currentLocation.y / 128;
        MapLocation actualLocation = new MapLocation(offsetX128*128 + x, offsetY128*128 + y);

        MapLocation altLocation = actualLocation.translate(-128, 0);
        if (currentLocation.distanceSquaredTo(altLocation) < currentLocation.distanceSquaredTo(actualLocation)) {
            actualLocation = altLocation;
        }
        altLocation = actualLocation.translate(128, 0);
        if (currentLocation.distanceSquaredTo(altLocation) < currentLocation.distanceSquaredTo(actualLocation)) {
            actualLocation = altLocation;
        }
        altLocation = actualLocation.translate(0, -128);
        if (currentLocation.distanceSquaredTo(altLocation) < currentLocation.distanceSquaredTo(actualLocation)) {
            actualLocation = altLocation;
        }
        altLocation = actualLocation.translate(0, 128);
        if (currentLocation.distanceSquaredTo(altLocation) < currentLocation.distanceSquaredTo(actualLocation)) {
            actualLocation = altLocation;
        }
        return actualLocation;
    }

    /**
     * Returns a path
     *
     * @return Direction
     */
     static void findPath(){
         // something
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
