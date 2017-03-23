package com.rvprg.raft.tests.functional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.SynchronousQueue;

import org.junit.Test;

import com.rvprg.raft.protocol.Raft;
import com.rvprg.raft.protocol.RaftObserver;
import com.rvprg.raft.protocol.impl.LogEntry;
import com.rvprg.raft.protocol.impl.RaftImpl;
import com.rvprg.raft.protocol.impl.RaftObserverImpl;
import com.rvprg.raft.tests.helpers.NetworkUtils;
import com.rvprg.raft.transport.MemberId;

public class RaftElectionSafetyTest {
    @Test(timeout = 60000)
    public void testElectionSafetyProperty_OneLeaderPerTerm()
            throws InterruptedException, NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        // This test creates a cluster containing clusterSize members. It then
        // starts all of them, and waits until a leader is elected. Then it
        // kills the leader by stopping heartbeating. A new leader is elected.
        // It then repeats this for a number of times specified by iteration.
        // Then it checks if terms were growing monotonically and that in each
        // term there was no more than one leader elected.
        int clusterSize = 5;
        int iterations = 10;

        List<Raft> rafts = new ArrayList<Raft>();
        Set<MemberId> members = new HashSet<MemberId>();
        for (Integer port : NetworkUtils.getRandomFreePorts(clusterSize)) {
            members.add(new MemberId("localhost", port));
        }

        CountDownLatch startLatch = new CountDownLatch(members.size());
        LinkedHashMap<Integer, Integer> termsAndLeaders = new LinkedHashMap<>();
        final SynchronousQueue<Raft> leaderBlockingQueue = new SynchronousQueue<>();

        RaftObserver observer = new RaftObserverImpl() {
            @Override
            public synchronized void electionWon(int term, Raft leader) {
                if (termsAndLeaders.containsKey(term)) {
                    termsAndLeaders.put(term, termsAndLeaders.get(term) + 1);
                } else {
                    termsAndLeaders.put(term, 1);
                }
                try {
                    leaderBlockingQueue.put(leader);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void started() {
                startLatch.countDown();
            }
        };

        for (MemberId member : members) {
            Set<MemberId> peers = (new HashSet<MemberId>(members));
            peers.remove(member);
            rafts.add(RaftTestUtils.getRaft(member.getHostName(), member.getPort(), peers, observer));
        }

        for (Raft raft : rafts) {
            raft.start();
        }

        startLatch.await();

        Method cancelHeartBeat = RaftImpl.class.getDeclaredMethod("cancelPeriodicHeartbeatTask", new Class[] {});
        cancelHeartBeat.setAccessible(true);

        for (int i = 0; i < iterations; ++i) {
            Raft currentLeader = leaderBlockingQueue.take();
            cancelHeartBeat.invoke(currentLeader, new Object[] {});
        }

        for (Raft raft : rafts) {
            raft.shutdown();
        }

        Integer prevTerm = 0;
        for (Integer term : termsAndLeaders.keySet()) {
            // Check terms are monotonically increasing
            assertTrue(prevTerm < term);
            // Always only one leader in each term
            assertEquals(1, termsAndLeaders.get(term).intValue());
            prevTerm = term;
        }
    }

    @Test(timeout = 60000)
    public void testElectionSafetyProperty_MostUpToDateLogWins_Case1()
            throws InterruptedException, NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        // This test creates a cluster containing clusterSize members. It then
        // creates a few entries in the log of the majority of the
        // candidates. Then it starts the election process and checks if a
        // leader is from that majority.
        // Case #1: More recent term wins.
        int clusterSize = 5;

        List<Raft> rafts = new ArrayList<Raft>();
        Set<MemberId> members = new HashSet<MemberId>();
        for (Integer port : NetworkUtils.getRandomFreePorts(clusterSize)) {
            members.add(new MemberId("localhost", port));
        }

        CountDownLatch startLatch = new CountDownLatch(members.size());
        final SynchronousQueue<Raft> leaderBlockingQueue = new SynchronousQueue<>();

        RaftObserver observer = new RaftObserverImpl() {
            @Override
            public synchronized void electionWon(int term, Raft leader) {
                try {
                    leaderBlockingQueue.put(leader);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void started() {
                startLatch.countDown();
            }
        };

        for (MemberId member : members) {
            Set<MemberId> peers = (new HashSet<MemberId>(members));
            peers.remove(member);
            rafts.add(RaftTestUtils.getRaft(member.getHostName(), member.getPort(), peers, observer));
        }

        Set<Raft> majority = new HashSet<Raft>();

        for (int i = 0; i < clusterSize / 2 + 1; ++i) {
            Raft raft = rafts.get(i);
            majority.add(raft);
            raft.getLog().append(new LogEntry(2, new byte[] { 2 }));
        }

        for (Raft raft : rafts) {
            raft.start();
        }

        startLatch.await();

        Raft currentLeader = leaderBlockingQueue.take();

        assertTrue(majority.contains(currentLeader));

        for (Raft raft : rafts) {
            raft.shutdown();
        }

    }

    @Test(timeout = 60000)
    public void testElectionSafetyProperty_MostUpToDateLogWins_Case2()
            throws InterruptedException, NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
        // This test creates a cluster containing clusterSize members. It then
        // creates a few entries in the log of the majority of the
        // candidates. Then it starts the election process and checks if a
        // leader is from that majority.
        // Case #2: Larger log wins.
        int clusterSize = 5;

        List<Raft> rafts = new ArrayList<Raft>();
        Set<MemberId> members = new HashSet<MemberId>();
        for (Integer port : NetworkUtils.getRandomFreePorts(clusterSize)) {
            members.add(new MemberId("localhost", port));
        }

        CountDownLatch startLatch = new CountDownLatch(members.size());
        final SynchronousQueue<Raft> leaderBlockingQueue = new SynchronousQueue<>();

        RaftObserver observer = new RaftObserverImpl() {
            @Override
            public synchronized void electionWon(int term, Raft leader) {
                try {
                    leaderBlockingQueue.put(leader);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void started() {
                startLatch.countDown();
            }
        };

        for (MemberId member : members) {
            Set<MemberId> peers = (new HashSet<MemberId>(members));
            peers.remove(member);
            rafts.add(RaftTestUtils.getRaft(member.getHostName(), member.getPort(), peers, observer));
        }

        Set<Raft> majority = new HashSet<Raft>();

        for (int i = 0; i < clusterSize; ++i) {
            Raft raft = rafts.get(i);
            majority.add(raft);
            raft.getLog().append(new LogEntry(2, new byte[] { 2 }));
            if (i < clusterSize / 2 + 1) {
                raft.getLog().append(new LogEntry(2, new byte[] { 3 }));
            }
        }

        for (Raft raft : rafts) {
            raft.start();
        }

        startLatch.await();

        Raft currentLeader = leaderBlockingQueue.take();

        assertTrue(majority.contains(currentLeader));

        for (Raft raft : rafts) {
            raft.shutdown();
        }

    }
}