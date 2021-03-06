package com.rvprg.sumi.tests;

import com.rvprg.sumi.configuration.Configuration;
import com.rvprg.sumi.log.*;
import com.rvprg.sumi.protocol.messages.ProtocolMessages.LogEntry;
import com.rvprg.sumi.sm.StateMachine;
import com.rvprg.sumi.sm.StreamableSnapshot;
import com.rvprg.sumi.sm.WritableSnapshot;
import com.rvprg.sumi.transport.MemberId;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class LogTest {
    private Log log;

    public LogTest(Log log) {
        this.log = log;
    }

    @Parameterized.Parameters(name = "{0}")
    public static Iterable<?> data() {
        return Arrays.asList(new InMemoryLogImpl(), new LevelDBLogImpl());
    }

    @Before
    public void init() throws IOException {
        Configuration configuration = Configuration.newBuilder()
                .logUri(URI.create("file:///" + UUID.randomUUID().toString()))
                .selfId(new MemberId("localhost", 1234)).build();
        log.initialize(configuration);
    }

    @After
    public void close() throws IOException {
        log.close();
        log.delete();
    }

    @Test
    public void testSimpleAppendAndGet() throws LogException {
        LogEntry logEntry1 = LogEntryFactory.create(1, new byte[0]);
        LogEntry logEntry2 = LogEntryFactory.create(1, new byte[0]);
        LogEntry logEntry3 = LogEntryFactory.create(2, new byte[0]);

        long index1 = log.append(logEntry1);
        long index2 = log.append(logEntry2);
        long index3 = log.append(logEntry3);

        assertEquals(1, index1);
        assertEquals(2, index2);
        assertEquals(3, index3);

        assertEquals(3, log.getLastIndex());

        assertEquals(logEntry1, log.get(1));
        assertEquals(logEntry2, log.get(2));
        assertEquals(logEntry3, log.get(3));

        assertNull(log.get(4));

        List<LogEntry> l1 = log.get(1, 1);
        assertEquals(1, l1.size());
        assertEquals(logEntry1, l1.get(0));

        List<LogEntry> l2 = log.get(1, 3);
        assertEquals(3, l2.size());
        assertEquals(logEntry1, l2.get(0));
        assertEquals(logEntry2, l2.get(1));
        assertEquals(logEntry3, l2.get(2));

        List<LogEntry> l3 = log.get(1, 10);
        assertEquals(3, l3.size());
        assertEquals(logEntry1, l2.get(0));
        assertEquals(logEntry2, l2.get(1));
        assertEquals(logEntry3, l2.get(2));

        List<LogEntry> l4 = log.get(1, -1);
        assertEquals(0, l4.size());

        List<LogEntry> l5 = log.get(-1, -1);
        assertEquals(0, l5.size());
    }

    @Test
    public void testComplexAppend_ShouldSucceed1() throws LogException {
        log.append(LogEntryFactory.create(1, new byte[0])); // 1
        log.append(LogEntryFactory.create(1, new byte[0])); // 2
        log.append(LogEntryFactory.create(1, new byte[0])); // 3
        log.append(LogEntryFactory.create(2, new byte[0])); // 4
        log.append(LogEntryFactory.create(3, new byte[0])); // 5

        assertEquals(5, log.getLastIndex());

        LogEntry logEntry1 = LogEntryFactory.create(2, new byte[0]);
        LogEntry logEntry2 = LogEntryFactory.create(2, new byte[0]);

        List<LogEntry> logEntries = new ArrayList<>();
        logEntries.add(logEntry1);
        logEntries.add(logEntry2);

        boolean res = log.append(4, 2, logEntries);
        assertTrue(res);
        assertEquals(6, log.getLastIndex());

        assertEquals(logEntry1, log.get(5));
        assertEquals(logEntry2, log.get(6));
    }

    @Test
    public void testComplexAppend_ShouldSucceed2() throws LogException {
        log.append(LogEntryFactory.create(1, new byte[0])); // 1
        log.append(LogEntryFactory.create(1, new byte[0])); // 2
        log.append(LogEntryFactory.create(1, new byte[0])); // 3
        log.append(LogEntryFactory.create(2, new byte[0])); // 4
        log.append(LogEntryFactory.create(3, new byte[0])); // 5

        assertEquals(5, log.getLastIndex());

        LogEntry logEntry1 = LogEntryFactory.create(2, new byte[0]);
        LogEntry logEntry2 = LogEntryFactory.create(2, new byte[0]);

        List<LogEntry> logEntries = new ArrayList<>();
        logEntries.add(logEntry1);
        logEntries.add(logEntry2);

        boolean res = log.append(5, 3, logEntries);
        assertTrue(res);
        assertEquals(7, log.getLastIndex());
    }

    @Test
    public void testComplexAppend_ShouldSucceed3() throws LogException {
        LogEntry[] logEntriesArr = new LogEntry[] {
                LogEntryFactory.create(1, new byte[0]), // 1
                LogEntryFactory.create(1, new byte[0]), // 2
                LogEntryFactory.create(1, new byte[0]), // 3
                LogEntryFactory.create(2, new byte[0]), // 4
                LogEntryFactory.create(3, new byte[0]) // 5
        };

        for (LogEntry le : logEntriesArr) {
            log.append(le);
        }

        assertEquals(5, log.getLastIndex());

        List<LogEntry> logEntries = new ArrayList<>();
        logEntries.add(logEntriesArr[3]);
        logEntries.add(logEntriesArr[4]);

        boolean res = log.append(3, 1, logEntries);
        assertTrue(res);

        assertEquals(5, log.getLastIndex());

        for (int i = 1; i < logEntriesArr.length; ++i) {
            assertEquals(logEntriesArr[i - 1], log.get(i));
        }
    }

    @Test
    public void testComplexAppend_ShouldFail1() throws LogException {
        log.append(LogEntryFactory.create(1, new byte[0])); // 1
        log.append(LogEntryFactory.create(1, new byte[0])); // 2
        log.append(LogEntryFactory.create(1, new byte[0])); // 3
        log.append(LogEntryFactory.create(2, new byte[0])); // 4
        log.append(LogEntryFactory.create(3, new byte[0])); // 5

        assertEquals(5, log.getLastIndex());

        LogEntry logEntry1 = LogEntryFactory.create(2, new byte[0]);
        LogEntry logEntry2 = LogEntryFactory.create(2, new byte[0]);

        List<LogEntry> logEntries = new ArrayList<>();
        logEntries.add(logEntry1);
        logEntries.add(logEntry2);

        boolean res = log.append(4, 1, logEntries);
        assertFalse(res);
        assertEquals(5, log.getLastIndex());
    }

    @Test
    public void testComplexAppend_ShouldFail2() throws LogException {
        assertFalse(log.append(3, 1, null));

        List<LogEntry> logEntries = new ArrayList<>();
        logEntries.add(LogEntryFactory.create(2, new byte[0]));
        logEntries.add(LogEntryFactory.create(2, new byte[0]));

        assertFalse(log.append(3, 1, logEntries));
        assertFalse(log.append(-1, 1, logEntries));
    }

    @Test
    public void testCommit() throws LogException {
        LogEntry[] logEntries = new LogEntry[] {
                LogEntryFactory.create(1),
                LogEntryFactory.create(1, new byte[] { 1 }),
                LogEntryFactory.create(1, new byte[] { 2 }),
                LogEntryFactory.create(1),
                LogEntryFactory.create(1, new byte[] { 3 })
        };

        for (LogEntry le : logEntries) {
            log.append(le);
        }

        final List<byte[]> commands = new ArrayList<>();
        StateMachine stateMachine = new StateMachine() {
            @Override
            public void apply(byte[] command) {
                commands.add(command);
            }

            @Override
            public void installSnapshot(StreamableSnapshot snapshot) {
            }

            @Override
            public WritableSnapshot getWritableSnapshot() {
                return null;
            }

        };

        assertEquals(1, log.getCommitIndex());

        log.commit(3, stateMachine);

        assertEquals(3, log.getCommitIndex());
        assertEquals(2, commands.size());

        assertArrayEquals(logEntries[1].getEntry().toByteArray(), commands.get(0));
        assertArrayEquals(logEntries[2].getEntry().toByteArray(), commands.get(1));

        log.commit(3, stateMachine);

        assertEquals(3, log.getCommitIndex());
        assertEquals(2, commands.size());

        assertArrayEquals(logEntries[1].getEntry().toByteArray(), commands.get(0));
        assertArrayEquals(logEntries[2].getEntry().toByteArray(), commands.get(1));

        log.commit(10, stateMachine);

        assertEquals(5, log.getCommitIndex());
        assertEquals(3, commands.size());

        assertArrayEquals(logEntries[1].getEntry().toByteArray(), commands.get(0));
        assertArrayEquals(logEntries[2].getEntry().toByteArray(), commands.get(1));
        assertArrayEquals(logEntries[4].getEntry().toByteArray(), commands.get(2));
    }

    @Test(expected = LogException.class)
    public void testTruncate_ShouldFail() throws LogException {
        log.append(LogEntryFactory.create(1, new byte[0])); // 1
        log.truncate(2);
    }

    @Test
    public void testTruncate() throws LogException {
        log.append(LogEntryFactory.create(1, new byte[0])); // 1
        log.append(LogEntryFactory.create(1, new byte[0])); // 2
        log.append(LogEntryFactory.create(1, new byte[0])); // 3
        log.append(LogEntryFactory.create(2, new byte[0])); // 4
        log.append(LogEntryFactory.create(3, new byte[0])); // 5

        assertEquals(5, log.getLastIndex());
        assertEquals(0, log.getFirstIndex());

        log.commit(log.getLastIndex(), new StateMachine() {
            @Override
            public void apply(byte[] command) {
            }

            @Override
            public void installSnapshot(StreamableSnapshot snapshot) {
            }

            @Override
            public WritableSnapshot getWritableSnapshot() {
                return null;
            }

        });

        log.truncate(4);

        assertEquals(5, log.getLastIndex());
        assertEquals(4, log.getFirstIndex());

        assertNull(log.get(3));
        assertNotNull(log.get(4));
    }
}
