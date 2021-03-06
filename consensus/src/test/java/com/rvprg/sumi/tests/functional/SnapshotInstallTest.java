package com.rvprg.sumi.tests.functional;

import static org.junit.Assert.assertTrue;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

import com.rvprg.sumi.log.LogException;
import com.rvprg.sumi.log.SnapshotInstallException;
import com.rvprg.sumi.protocol.AddCatchingUpMemberResult;
import com.rvprg.sumi.protocol.ApplyCommandResult;
import com.rvprg.sumi.protocol.Consensus;
import com.rvprg.sumi.protocol.ConsensusImpl;
import com.rvprg.sumi.protocol.ConsensusEventListener;
import com.rvprg.sumi.protocol.ConsensusEventListenerImpl;
import com.rvprg.sumi.tests.helpers.NetworkUtils;
import com.rvprg.sumi.tests.helpers.ConsensusFunctionalBase;
import com.rvprg.sumi.transport.MemberId;

public class SnapshotInstallTest extends ConsensusFunctionalBase {

    @Test
    public void testInstallSnapshot()
            throws InterruptedException, NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, LogException,
            FileNotFoundException, SnapshotInstallException, IOException {
        // This test creates a cluster of clusterSize members. Then it applies
        // logSize different commands. Next, it takes a snapshot. Adds a few
        // more commands. Then it adds a catching up member.
        // Catching up member should receive a snapshot. Once the snapshot is
        // installed on the catching up member, it issues more SM commands, and
        // verifies all SMs and logs are consistent.
        int clusterSize = 3;
        int logSize = 4;

        Method scheduleLogCompactionTask = ConsensusImpl.class.getDeclaredMethod("scheduleLogCompactionTask", new Class[] {});
        scheduleLogCompactionTask.setAccessible(true);

        RaftCluster cluster = new RaftCluster(clusterSize, clusterSize, clusterSize, 9000, 10000);
        cluster.start();

        Consensus currentLeader = cluster.getLeader();
        for (int i = 0; i < logSize; ++i) {
            byte[] buff = ByteBuffer.allocate(4).putInt(i).array();
            ApplyCommandResult applyCommandResult = currentLeader.applyCommand(buff);
            if (applyCommandResult.getResult() != null) {
                try {
                    applyCommandResult.getResult().get(3000, TimeUnit.MILLISECONDS);
                } catch (TimeoutException | InterruptedException | ExecutionException e) {
                    // fine
                }
            }
        }

        cluster.waitUntilCommitAdvances();
        cluster.waitUntilFollowersAdvance();

        // Take a snapshot
        for (Consensus r : cluster.getRafts()) {
            scheduleLogCompactionTask.invoke(r, new Object[] {});
        }

        for (int i = 0; i < logSize; ++i) {
            byte[] buff = ByteBuffer.allocate(4).putInt(i).array();
            ApplyCommandResult applyCommandResult = currentLeader.applyCommand(buff);
            if (applyCommandResult.getResult() != null) {
                try {
                    applyCommandResult.getResult().get(3000, TimeUnit.MILLISECONDS);
                } catch (TimeoutException | InterruptedException | ExecutionException e) {
                    // fine
                }
            }
        }

        cluster.waitUntilCommitAdvances();
        cluster.waitUntilFollowersAdvance();

        MemberId newMemberId = new MemberId("localhost", NetworkUtils.getRandomFreePort());
        Set<MemberId> peers = (new HashSet<MemberId>(cluster.getMembers()));
        CountDownLatch newMemberStartLatch = new CountDownLatch(1);
        CountDownLatch newMemberShutdownLatch = new CountDownLatch(1);

        ConsensusEventListener newMemberListener = new ConsensusEventListenerImpl() {
            @Override
            public void started() {
                newMemberStartLatch.countDown();
            }

            @Override
            public void shutdown() {
                newMemberShutdownLatch.countDown();
            }
        };

        Consensus newRaftMember = getRaft(newMemberId.getHostName(), newMemberId.getPort(), peers, 9000, 10000, newMemberListener);
        newRaftMember.becomeCatchingUpMember();
        newRaftMember.start();

        newMemberStartLatch.await();

        // Phase 1: Add as a catching up server.
        AddCatchingUpMemberResult addCatchingUpMemberResult = currentLeader.addCatchingUpMember(newMemberId);
        assertTrue(addCatchingUpMemberResult.getResult() != null);
        assertTrue(addCatchingUpMemberResult.getResult());

        // Polling.
        boolean finished = false;
        while (!finished) {
            finished = true;
            if (newRaftMember.getLog().getCommitIndex() != currentLeader.getLog().getCommitIndex()) {
                finished = false;
                Thread.sleep(100);
                break;
            }
        }

        // Phase 2: Add as a normal member
        ApplyCommandResult addMemberDynamicallyResult = currentLeader.addMemberDynamically(newMemberId);
        newRaftMember.becomeVotingMember();
        assertTrue(addMemberDynamicallyResult.getResult() != null);
        final AtomicBoolean addSuccess = new AtomicBoolean(false);
        try {
            addSuccess.set(addMemberDynamicallyResult.getResult().get(10000, TimeUnit.MILLISECONDS));
        } catch (TimeoutException | InterruptedException | ExecutionException e) {
            addSuccess.set(false);
        }

        cluster.getRafts().add(newRaftMember);
        cluster.waitUntilCommitAdvances();
        cluster.waitUntilFollowersAdvance();

        cluster.shutdown();
        newRaftMember.shutdown();
        newMemberShutdownLatch.await();

        assertTrue(addSuccess.get());

        cluster.checkLastIndexes();
        cluster.checkFirstIndexes();
        cluster.checkLogConsistency();
    }
}
