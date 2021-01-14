package Rushbot3;
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

    static final Direction[] cardinals = {
            Direction.NORTH,
            Direction.EAST,
            Direction.SOUTH,
            Direction.WEST,
    };

    static int turnCount;
    static MapLocation enemyBaseLoc = new MapLocation(0, 0);
    static int homeID;
    static MapLocation homeLoc;
    static Direction directionality = Direction.CENTER;
    static int handedness;
    static Set<Integer> flagsSeen = new HashSet<Integer>();
    static Set<Integer> seenMucks = new HashSet<Integer>();
    static Set<MapLocation> enemyBasesToCapture = new HashSet<MapLocation>();
    static Set<MapLocation> enemyBasesCaptured = new HashSet<MapLocation>();

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

        if (rc.getType() == RobotType.MUCKRAKER) {
            if (Math.random() > .6) {
                directionality = directions[(int) (Math.random() * directions.length)];
            } else {
                directionality = cardinals[(int) (Math.random() * cardinals.length)];
            }
        }

        //Determine handedness
        if (Math.random() > .5){
            handedness = 0; //left
        } else {
            handedness = 1; //right
        }

        System.out.println("I'm a " + rc.getType() + " and I just got created!");
        while (true) {
            turnCount += 1;
            // Try/catch blocks stop unhandled exceptions, which cause your robot to freeze
            try {
                // Here, we've separated the controls into a different method for each RobotType.
                // You may rewrite this into your own control structure if you wish.
                System.out.println("I'm a " + rc.getType() + "! Location " + rc.getLocation());
                System.out.println("There are enemy bases at " + enemyBaseLoc + enemyBasesToCapture);
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

    ////////////////////////////////////////////////////////////////////////////////////////
    // Enlightenment Center
    static void runEnlightenmentCenter() throws GameActionException {
        Team enemyTeam = rc.getTeam().opponent();
        Team allyTeam = rc.getTeam();
        int currInfluence = rc.getInfluence(); //gets current influence
        int senseRadius = rc.getType().sensorRadiusSquared;

        RobotInfo[] sensed = rc.senseNearbyRobots(senseRadius, enemyTeam);
        RobotInfo[] sensedAllies = rc.senseNearbyRobots(senseRadius, allyTeam);
        RobotInfo[] sensedAlliesCloseby = rc.senseNearbyRobots(2, allyTeam);

        //If we hit 751 votes, no longer need to bid
        boolean shouldBid = true;
        int currentVotes = rc.getTeamVotes();
        if (currentVotes >= 751) {
            shouldBid = false;
        }

        int numNearbySlans = 0;

        for (RobotInfo ally : sensedAlliesCloseby) {
            if (ally.type == RobotType.MUCKRAKER && !seenMucks.contains(ally.ID)){
                seenMucks.add(ally.ID);
            }
        }

        // Get flag info from mucks
        for (int muckID : seenMucks) {
            if (rc.canGetFlag(muckID)){
                int flagNum = rc.getFlag(muckID);
                if (flagNum > 10 && !flagsSeen.contains(flagNum)){
                    enemyBaseLoc = decodeFlag(flagNum);
                    if (trySetFlag(flagNum)){
                        flagsSeen.add(flagNum);
                        System.out.println("Received from muck. Set flag: "+flagNum + enemyBaseLoc);
                    }
                }
            } else {
                seenMucks.remove(muckID);
            }
        }

        //There is a target base to go for
        if (enemyBaseLoc.x != 0) {
            if (currInfluence <= 75){
                return;
            } else {
                if (rc.canBid(1) && shouldBid) {
                    rc.bid(1);
                    System.out.println("I bid: 1");
                }
                if (Math.random() > .1) {
                    System.out.println("Built targeted politician.");
                    buildRobot(RobotType.POLITICIAN, currInfluence - 15);
                }
                else {
                    buildRobot(RobotType.MUCKRAKER, 5);
                }
            }
        }

        //bids
        if (currInfluence > 90 && turnCount > 150 && shouldBid) {
            if (rc.canBid(currInfluence/3)) {
                currInfluence -= currInfluence/3;
                rc.bid(currInfluence/3);
                System.out.println("I bid: "+ currInfluence/3);
            }
        } else if (shouldBid) {
            if (rc.canBid(2) && Math.random() > .6) {
                currInfluence -= 2;
                rc.bid(2);
                System.out.println("I bid: 2");
            }
        }

        // Want to conserve some influence or too many friendly units around
        if (currInfluence <= 50 || sensedAllies.length > 40) {
            System.out.println("Conserving or too many allies around");
            return;
        }

        //Sense 3-10 enemies in vicinity, so build politicians to kill them
        if (sensed.length >= 3 && sensed.length < 10) {
            for (Direction dir : directions) {
                if (rc.canBuildRobot(RobotType.POLITICIAN, dir, currInfluence/2)) {
                    System.out.println("Enemies around, building polis");
                    rc.buildRobot(RobotType.POLITICIAN, dir, currInfluence/2);
                    return;
                }
            }
        } else if (sensed.length > 20) { //Too many enemies surrounding, just turtle up
            System.out.println("Turtling...");
            return;
        }

        //choose what robot to build
        RobotType toBuild;
        boolean buildBool;
        int buildInfluence = currInfluence/2;
        double polPercent;
        double slanPercent;

        if (turnCount < 300) {
            polPercent = .80;
            slanPercent = .50;
        } else if (turnCount < 1000){
            polPercent = .75;
            slanPercent = .40;
        } else if (turnCount < 1500) {
            polPercent = .40;
            slanPercent = .25;
        } else {
            polPercent = .30;
            slanPercent = .15;
        }

        if (buildInfluence < 25) {
            buildBool = Math.random() > .4;
            if (turnCount < 200){
                buildInfluence = 25;
            } else {
                buildInfluence = 5;
            }
            toBuild = RobotType.MUCKRAKER;
        } else {
            buildBool = Math.random() > .3;
            double percent = Math.random();
            if (percent > polPercent) {
                toBuild = RobotType.POLITICIAN;
            } else if (percent > slanPercent) {
                buildInfluence = buildInfluence - buildInfluence % 20 + 1;
                toBuild = RobotType.SLANDERER;
            } else {
                buildInfluence = 5;
                toBuild = RobotType.MUCKRAKER;
            }
        }

        //builds robot in random direction
        if (buildBool) {
            buildRobot(toBuild, buildInfluence);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////
    // Politician
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

        boolean seeMurkorPol = false;

        if (turnCount <= 12) {
            for (RobotInfo ally :rc.senseNearbyRobots(2, allyTeam)) {
                if (ally.getType().canBid()){
                    homeID = ally.getID();
                    homeLoc = ally.getLocation();
                }
            }
        } else if (turnCount > 800) {
            directionality = Direction.CENTER;
        }

        // Can attack an enemy base or muckraker
        for (RobotInfo enemy : attackable) {
            if (enemy.type == RobotType.ENLIGHTENMENT_CENTER){
                if (rc.canEmpower(actionRadius)){
                    rc.empower(actionRadius);
                    return;
                }
            }
            // finds a muckraker
            if (enemy.type == RobotType.MUCKRAKER || enemy.type == RobotType.POLITICIAN) {
                seeMurkorPol = true;
            }
        }

        // Move toward a sensed enemy base
        for (RobotInfo enemy : sensed) {
            if (enemy.type == RobotType.ENLIGHTENMENT_CENTER){
                MapLocation enemyLoc = enemy.getLocation();
                int flagNum = codeFlag(enemyLoc, 0); // Set a flag for enemy base location
                if (trySetFlag(flagNum)){
                    flagsSeen.add(flagNum);
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
            if (neutral.type == RobotType.ENLIGHTENMENT_CENTER && (neutral.getInfluence() <= 100 || Math.random() > .6)){
                if (rc.canEmpower(actionRadius)){
                    rc.empower(actionRadius);
                    return;
                }
            }
        }

        // Move toward a neutral base if < 100 influence
        for (RobotInfo neutral : sensedNeutral) {
            if (neutral.getType().canBid() && (neutral.getInfluence() <= 100 || Math.random() > .3)){
                Direction dirMove = currentloc.directionTo(neutral.getLocation());
                if (rc.canMove(dirMove)) {
                    rc.move(dirMove);
                    return;
                }
            }
        }

        //Check EC flag
        if (rc.canGetFlag(homeID)) {
            int ecFlag = rc.getFlag(homeID);
            if (ecFlag > 10 && enemyBaseLoc.x == 0 && !flagsSeen.contains(ecFlag)) {
                flagsSeen.add(ecFlag);
                enemyBaseLoc = decodeFlag(ecFlag);
                System.out.println("Got flag from EC. Target location: " + enemyBaseLoc);
            }
        }

        //Decode neighboring ally flags
        for (RobotInfo ally : sensedAlly) {
            int allyFlag = rc.getFlag(ally.getID());
            if (allyFlag > 10 && !flagsSeen.contains(allyFlag)) {
                flagsSeen.add(allyFlag);
                enemyBaseLoc = decodeFlag(allyFlag);
                System.out.println("Decoded a flag with location: " + enemyBaseLoc);
                break;
            }
        }

        //Check if enemybaseloc has already been captured, if so, set flag with extrainfo = 1.
        for (RobotInfo ally : sensedAlly) {
            if (ally.type == RobotType.ENLIGHTENMENT_CENTER && ally.getLocation().equals(enemyBaseLoc)) {
                int flagNum = codeFlag(ally.getLocation(), 1); // Set a flag for enemy base location
                if (trySetFlag(flagNum)) {
                    flagsSeen.add(flagNum);
                    System.out.println("Set Flag " + flagNum + "(Captured)");
                    enemyBaseLoc = new MapLocation(0,0);
                }
                break;
            }
        }

        // Move toward enemyBase if not (0,0)
        if (enemyBaseLoc.x != 0 && enemyBaseLoc.y != 0) {
            findPath(enemyBaseLoc, handedness);
        }

        // If we saw a murk, empower w 30% chance
        if (seeMurkorPol && Math.random() >= .7){
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

        //Move w certain chance in best direction w largest passability or based on directionality
        if (directionality.equals(Direction.CENTER)) {
            if (tryMove(findBestDirection())) {
                System.out.println("I moved randomly!");
                return;
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////
    // Slanderer
    static void runSlanderer() throws GameActionException {
        Team enemyTeam = rc.getTeam().opponent();
        Team allyTeam = rc.getTeam();
        MapLocation currentloc = rc.getLocation();
        int senseRadius = rc.getType().sensorRadiusSquared;
        RobotInfo[] sensed = rc.senseNearbyRobots(senseRadius, enemyTeam);

        if (turnCount <= 2) {
            for (RobotInfo ally :rc.senseNearbyRobots(2, allyTeam)) {
                if (ally.type == RobotType.ENLIGHTENMENT_CENTER){
                    homeID = ally.getID();
                    homeLoc = ally.getLocation();
                }
            }
        } else if (turnCount > 800) {
            directionality = Direction.CENTER;
        }

        //Move away from mucks or pols
        for (RobotInfo enemy : sensed) {
            //Sense enemy base
            if (enemy.type == RobotType.ENLIGHTENMENT_CENTER) {
                MapLocation enemyLoc = enemy.getLocation();
                int flagNum = codeFlag(enemyLoc, 0); // Set a flag for enemy base location
                if (trySetFlag(flagNum)) {
                    System.out.println("Set Flag: " + flagNum);
                    directionality = directionality.opposite();
                }
            }

            // move away from a muck or pol
            if (enemy.type == RobotType.POLITICIAN || enemy.type == RobotType.MUCKRAKER){
                Direction dirMove = currentloc.directionTo(enemy.getLocation()).opposite();
                if (rc.canMove(dirMove)) {
                    System.out.println("Running away toward "+dirMove);
                    rc.move(dirMove);
                    return;
                }
            }
        }

        if (currentloc.distanceSquaredTo(homeLoc) >= 13) {
            return;
        }

        //Move w certain chance in best direction w largest passability or based on directionality
        if (directionality.equals(Direction.CENTER)) {
            if (tryMove(findBestDirection())) {
                System.out.println("I moved randomly!");
                return;
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////
    // Muckraker
    static void runMuckraker() throws GameActionException {
        Team enemyTeam = rc.getTeam().opponent();
        Team allyTeam = rc.getTeam();
        MapLocation currentloc = rc.getLocation();
        int actionRadius = rc.getType().actionRadiusSquared;
        int senseRadius = rc.getType().sensorRadiusSquared;
        int detectRadius = rc.getType().detectionRadiusSquared;

        RobotInfo[] sensedAlly = rc.senseNearbyRobots(senseRadius, allyTeam);

        RobotInfo[] attackable = rc.senseNearbyRobots(actionRadius, enemyTeam);
        RobotInfo[] sensed = rc.senseNearbyRobots(senseRadius, enemyTeam);
        RobotInfo[] detected = rc.senseNearbyRobots(detectRadius, enemyTeam);

        if (turnCount <= 12) {
            for (RobotInfo ally :rc.senseNearbyRobots(2, allyTeam)) {
                if (ally.getType().canBid()){
                    homeID = ally.getID();
                    homeLoc = ally.getLocation();
                }
            }
        } else if (turnCount > 300) {
            directionality = Direction.CENTER;
        }

        for (RobotInfo ally : sensedAlly) {
            int allyFlag = rc.getFlag(ally.getID());
            if (allyFlag > 10 && !flagsSeen.contains(allyFlag)) {
                flagsSeen.add(allyFlag);
                enemyBaseLoc = decodeFlag(allyFlag);
                System.out.println("Decoded a flag with location: " + enemyBaseLoc);
                break;
            }
        }

        for (RobotInfo ally : sensedAlly) {
            if (ally.type == RobotType.ENLIGHTENMENT_CENTER && ally.getLocation().equals(enemyBaseLoc)) {
                int flagNum = codeFlag(ally.getLocation(), 1); // Set a flag for enemy base location
                if (trySetFlag(flagNum)) {
                    flagsSeen.add(flagNum);
                    System.out.println("Set Flag " + flagNum + "(Captured)");
                    enemyBaseLoc = new MapLocation(0,0);
                }
            }
        }

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
            //Sense enemy base
            if (enemy.type == RobotType.ENLIGHTENMENT_CENTER) {
                MapLocation enemyLoc = enemy.getLocation();
                enemyBaseLoc = enemyLoc;
                int flagNum = codeFlag(enemyLoc, 0); // Set a flag for enemy base location
                if (!flagsSeen.contains(flagNum) && trySetFlag(flagNum)) {
                    flagsSeen.add(flagNum);
                    System.out.println("Set Flag: " + flagNum);
                    directionality = Direction.CENTER;
                }
            }
            //Sense slanderer
            if (enemy.getType().canBeExposed() && turnCount > 300) {
                Direction dirMove = currentloc.directionTo(enemy.getLocation());
                if (rc.canMove(dirMove)) { // move toward a slanderer
                    rc.move(dirMove);
                    return;
                }
            }
        }

        //Move w certain chance in best direction w largest passability or based on directionality
        if (directionality.equals(Direction.CENTER)) {
            if (tryMove(findBestDirection())) {
                System.out.println("I moved randomly!");
                return;
            }
        } else {
            if (tryMove(directionality)){
                System.out.println("I moved directionally toward "+ directionality + " !");
                return;
            } else if (!rc.canSenseLocation(currentloc.add(directionality))) { // Hit map edge
                if (handedness == 0) {
                    directionality = directionality.rotateRight().rotateRight();
                } else {
                    directionality = directionality.rotateLeft().rotateLeft();
                }
                System.out.println("I hit the map edge! Going in direction " +directionality + " now!");
            } else {
                if (tryMove(findBestDirection())) {
                    System.out.println("Could not move directionally, so I moved randomly!");
                    return;
                }
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////
    // Flag Communication

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
     * Attempts to decode a flag given a flag int.
     * extraninfo = 0 --> enemy base not captured
     * extrainfo = 1 --> enemy base converted
     * extrainfo = 2 --> neutral base
     *
     * @return MapLocation
     */
    static MapLocation decodeFlag(int flag) throws GameActionException {
        int y = flag % 128;
        int x = (flag/128) % 128;
        int extrainfo = flag / 128 / 128;

        if (flag == 0 || extrainfo == 1) {
            return new MapLocation(0, 0);
        }

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

    ////////////////////////////////////////////////////////////////////////////////////////
    // Robot Building

    /**
     * Attempts to build robot in a random direction.
     *
     * @param toBuild robot type
     * @return true if a move was performed
     */

    static boolean buildRobot(RobotType toBuild, int buildInfluence) throws GameActionException{
        List<Direction> shuffledDir =  Arrays.asList(directions);
        Collections.shuffle(shuffledDir);
        for (Direction dir : shuffledDir) {
            if (rc.canBuildRobot(toBuild, dir, buildInfluence)) {
                System.out.println("I made a "+ toBuild + " to the " + dir);
                rc.buildRobot(toBuild, dir, buildInfluence);
                return true;
            }
        }
        return false;
    }

    ////////////////////////////////////////////////////////////////////////////////////////
    // Movement
    /**
     * Returns a path depending on handedness.
     * handedness = 0: left-handed
     * handedness = 1: right-handed
     *
     * @return Direction
     */
    static final double passabilityThreshold = 0.25;
    static Direction bugDirection = null;

    static void findPath(MapLocation target, int handedness) throws GameActionException{
        Direction d = rc.getLocation().directionTo(target);
        if (rc.getLocation().equals(target)) {
            // do something else, now that you're there
            // here we'll just explode
            ;
        } else if (rc.isReady()) {
            if (rc.canMove(d) && (rc.sensePassability(rc.getLocation().add(d)) >= passabilityThreshold || Math.random() > .92)) {
                System.out.println("Moved toward target: " + target);
                rc.move(d);
                bugDirection = null;
            } else {
                if (bugDirection == null) {
                    bugDirection = d;
                }
                for (int i = 0; i < 8; ++i) {
                    if (rc.canMove(bugDirection) && rc.sensePassability(rc.getLocation().add(bugDirection)) >= passabilityThreshold) {
//                        rc.setIndicatorDot(rc.getLocation().add(bugDirection), 0, 255, 255);
                        System.out.println("Moved toward target: " + target);
                        rc.move(bugDirection);
                        if (handedness == 0) {
                            bugDirection = bugDirection.rotateLeft();
                        } else {
                            bugDirection = bugDirection.rotateRight();
                        }
                        break;
                    }
//                    rc.setIndicatorDot(rc.getLocation().add(bugDirection), 255, 0, 0);
                    if (handedness == 0) {
                        bugDirection = bugDirection.rotateRight();
                    } else {
                        bugDirection = bugDirection.rotateLeft();
                    }
                }
            }
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

        if (Math.random() > .20) { // 20% chance its random
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
     * Attempts to move in a given direction.
     *
     * @param dir The intended direction of movement
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryMove(Direction dir) throws GameActionException {
//        System.out.println("I am trying to move " + dir + "; " + rc.isReady() + " " + rc.getCooldownTurns() + " " + rc.canMove(dir));
        if (rc.canMove(dir)) {
            rc.move(dir);
            return true;
        } else return false;
    }
}
