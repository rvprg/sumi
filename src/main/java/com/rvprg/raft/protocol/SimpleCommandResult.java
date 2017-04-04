package com.rvprg.raft.protocol;

import com.rvprg.raft.transport.MemberId;

public class SimpleCommandResult<T> implements CommandResult<T> {
    private final T result;
    private final MemberId leaderId;

    @Override
    public MemberId getLeaderId() {
        return leaderId;
    }

    @Override
    public T getResult() {
        return result;
    }

    public SimpleCommandResult(T result, MemberId leaderId) {
        this.result = result;
        this.leaderId = leaderId;
    }
}
