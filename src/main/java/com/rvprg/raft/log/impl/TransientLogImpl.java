package com.rvprg.raft.log.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.google.protobuf.ByteString;
import com.rvprg.raft.log.Log;
import com.rvprg.raft.protocol.messages.ProtocolMessages.LogEntry;
import com.rvprg.raft.protocol.messages.ProtocolMessages.LogEntry.LogEntryType;
import com.rvprg.raft.sm.StateMachine;

public class TransientLogImpl implements Log {
    private final ArrayList<LogEntry> log = new ArrayList<LogEntry>();
    private int commitIndex = 0;
    private int firstIndex = 0;

    public TransientLogImpl() {
        init("");
    }

    @Override
    public synchronized void close() throws IOException {
        // nop
    }

    @Override
    public synchronized int getCommitIndex() {
        return commitIndex;
    }

    @Override
    public synchronized boolean append(int prevLogIndex, int prevLogTerm, List<LogEntry> logEntries) {
        if (logEntries == null || logEntries.isEmpty()) {
            return false;
        }

        LogEntry prevEntry = get(prevLogIndex);
        if (prevEntry == null) {
            return false;
        }

        if (prevEntry.getTerm() != prevLogTerm) {
            return false;
        }

        LogEntry newNextEntry = logEntries.get(0);

        int nextEntryIndex = prevLogIndex + 1;
        LogEntry nextEntry = get(nextEntryIndex);
        if (nextEntry != null && nextEntry.getTerm() != newNextEntry.getTerm()) {
            while (log.size() > nextEntryIndex) {
                log.remove(log.size() - 1);
            }
        }

        for (int i = nextEntryIndex, j = 0; j < logEntries.size(); ++i, ++j) {
            insert(i, logEntries.get(j));
        }

        return true;
    }

    private void insert(int index, LogEntry logEntry) {
        if (index > log.size() - 1) {
            log.add(logEntry);
        } else {
            log.set(index, logEntry);
        }
    }

    @Override
    public synchronized LogEntry get(int index) {
        if (index >= log.size() || index < 0) {
            return null;
        }
        return log.get(index);
    }

    @Override
    public synchronized LogEntry getLast() {
        return log.get(getLastIndex());
    }

    @Override
    public synchronized int getLastIndex() {
        return log.size() - 1;
    }

    @Override
    public synchronized List<LogEntry> get(int nextIndex, int maxNum) {
        if (nextIndex > log.size() - 1 || nextIndex < 0 || maxNum <= 0) {
            return new ArrayList<LogEntry>();
        }

        if (nextIndex + maxNum > log.size() - 1) {
            return log.subList(nextIndex, log.size());
        }

        return log.subList(nextIndex, nextIndex + maxNum);
    }

    @Override
    public int append(LogEntry logEntry) {
        log.add(logEntry);
        return log.size() - 1;
    }

    @Override
    public synchronized void init(String name) {
        log.clear();
        commitIndex = 1;
        firstIndex = 0;
        LogEntry logEntry = LogEntry.newBuilder().setTerm(0).setType(LogEntryType.NoOperationCommand).setEntry(ByteString.copyFrom(new byte[0])).build();
        log.add(logEntry);
    }

    @Override
    public String toString() {
        return "Transient log implementation";
    }

    @Override
    public synchronized int commit(int commitUpToIndex, StateMachine stateMachine) {
        if (commitUpToIndex > getCommitIndex()) {
            int newIndex = Math.min(commitUpToIndex, getLastIndex());

            for (int i = getCommitIndex() + 1; i <= newIndex; ++i) {
                LogEntry logEntry = get(i);
                if (logEntry.getType() == LogEntryType.StateMachineCommand) {
                    stateMachine.apply(logEntry.getEntry().toByteArray());
                }
            }

            this.commitIndex = newIndex;
        }
        return this.commitIndex;

    }

    @Override
    public synchronized int getFirstIndex() {
        return firstIndex;
    }

    @Override
    public void delete() {
        // nop
    }

}