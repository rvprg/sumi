package com.rvprg.raft.protocol;

import com.rvprg.raft.configuration.Configuration;
import com.rvprg.raft.protocol.impl.ApplyCommandResult;
import com.rvprg.raft.transport.MemberId;

public interface Raft extends MessageConsumer {
    void start() throws InterruptedException;

    void shutdown() throws InterruptedException;

    boolean isStarted();

    int getCurrentTerm();

    Role getRole();

    Configuration getConfiguration();

    Log getLog();

    ApplyCommandResult applyCommand(byte[] command);

    ApplyCommandResult addMemberDynamically(MemberId member);

    ApplyCommandResult removeMemberDynamically(MemberId member);
}
