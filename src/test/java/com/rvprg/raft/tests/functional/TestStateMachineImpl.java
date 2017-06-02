package com.rvprg.raft.tests.functional;

import java.io.IOException;
import java.io.OutputStream;

import com.rvprg.raft.log.SnapshotInstallException;
import com.rvprg.raft.sm.KeyValueStore;
import com.rvprg.raft.sm.ReadableSnapshot;
import com.rvprg.raft.sm.StateMachine;
import com.rvprg.raft.sm.WritableSnapshot;

public class TestStateMachineImpl implements StateMachine {
    private volatile KeyValueStore store = new KeyValueStore();

    @Override
    public void apply(byte[] command) {
        // TODO Auto-generated method stub

    }

    @Override
    public void installSnapshot(ReadableSnapshot snapshot) throws SnapshotInstallException {
        try {
            store = KeyValueStore.read(snapshot.read());
        } catch (Exception e) {
            // FIXME:
            throw new SnapshotInstallException();
        }
    }

    @Override
    public WritableSnapshot getWritableSnapshot() {
        final KeyValueStore thisStore = new KeyValueStore(store);
        return new WritableSnapshot() {
            @Override
            public void write(OutputStream stream) throws IOException {
                thisStore.write(stream);
            }
        };
    }

}