package com.rvprg.sumi.tests;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.rvprg.sumi.Module;
import com.rvprg.sumi.configuration.Configuration;
import com.rvprg.sumi.protocol.MessageConsumer;
import com.rvprg.sumi.protocol.messages.ProtocolMessages;
import com.rvprg.sumi.protocol.messages.ProtocolMessages.*;
import com.rvprg.sumi.protocol.messages.ProtocolMessages.RaftMessage.MessageType;
import com.rvprg.sumi.tests.helpers.EchoServer;
import com.rvprg.sumi.tests.helpers.MemberConnectorListenerTestableImpl;
import com.rvprg.sumi.transport.ActiveMember;
import com.rvprg.sumi.transport.ChannelPipelineInitializer;
import com.rvprg.sumi.transport.MemberConnector;
import com.rvprg.sumi.transport.MemberId;
import io.netty.channel.Channel;
import org.junit.Test;
import org.mockito.stubbing.Answer;

import java.net.URI;
import java.util.concurrent.CountDownLatch;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class MessageDispatcherTest {

    private RaftMessage getRequestVoteInstance() {
        RequestVote requestVote = ProtocolMessages.RequestVote.newBuilder()
                .setCandidateId("test")
                .setTerm(1)
                .setLastLogIndex(2)
                .setLastLogTerm(3)
                .build();
        RaftMessage requestVoteRaftMessage = ProtocolMessages.RaftMessage.newBuilder()
                .setRequestVote(requestVote)
                .setType(MessageType.RequestVote).build();

        return requestVoteRaftMessage;
    }

    private RaftMessage getRequestVoteResponseInstance() {
        RequestVoteResponse requestVoteResponse = ProtocolMessages.RequestVoteResponse.newBuilder()
                .setTerm(1)
                .setVoteGranted(true)
                .build();
        RaftMessage requestVoteResponseRaftMessage = ProtocolMessages.RaftMessage.newBuilder()
                .setRequestVoteResponse(requestVoteResponse)
                .setType(MessageType.RequestVoteResponse).build();
        return requestVoteResponseRaftMessage;
    }

    private RaftMessage getAppendEntriesInstance() {
        AppendEntries requestAppendEntries = ProtocolMessages.AppendEntries.newBuilder()
                .setTerm(1)
                .setLeaderId("test")
                .setPrevLogIndex(0)
                .setLeaderCommitIndex(1)
                .build();
        RaftMessage requestAppendEntriesRaftMessage = ProtocolMessages.RaftMessage.newBuilder()
                .setAppendEntries(requestAppendEntries)
                .setType(MessageType.AppendEntries).build();
        return requestAppendEntriesRaftMessage;
    }

    private RaftMessage getAppendEntriesResponseInstance() {
        AppendEntriesResponse requestAppendEntriesResponse = ProtocolMessages.AppendEntriesResponse.newBuilder()
                .setTerm(1)
                .setSuccess(true)
                .build();
        RaftMessage requestAppendEntriesRaftMessage = ProtocolMessages.RaftMessage.newBuilder()
                .setAppendEntriesResponse(requestAppendEntriesResponse)
                .setType(MessageType.AppendEntriesResponse).build();
        return requestAppendEntriesRaftMessage;
    }

    private void checkRequestVoteDispatch(MessageConsumer messageConsumer, Channel channel) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);

        doAnswer((Answer<Void>) invocation -> {
            latch.countDown();
            return null;
        }).when(messageConsumer).consumeRequestVote(any(ActiveMember.class), any(RequestVote.class));

        RaftMessage requestVoteRaftMessage = getRequestVoteInstance();
        channel.writeAndFlush(requestVoteRaftMessage);

        latch.await();
    }

    private void checkRequestVoteResponseDispatch(MessageConsumer messageConsumer, Channel channel) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);

        doAnswer((Answer<Void>) invocation -> {
            latch.countDown();
            return null;
        }).when(messageConsumer).consumeRequestVoteResponse(any(ActiveMember.class), any(RequestVoteResponse.class));

        RaftMessage requestVoteResponseRaftMessage = getRequestVoteResponseInstance();
        channel.writeAndFlush(requestVoteResponseRaftMessage);

        latch.await();

        verify(messageConsumer, times(1)).consumeRequestVoteResponse(any(), eq(requestVoteResponseRaftMessage.getRequestVoteResponse()));
    }

    private void checkAppendEntriesDispatch(MessageConsumer messageConsumer, Channel channel) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);

        doAnswer((Answer<Void>) invocation -> {
            latch.countDown();
            return null;
        }).when(messageConsumer).consumeAppendEntries(any(ActiveMember.class), any(AppendEntries.class));

        RaftMessage requestAppendEntriesRaftMessage = getAppendEntriesInstance();
        channel.writeAndFlush(requestAppendEntriesRaftMessage);

        latch.await();

        verify(messageConsumer, times(1)).consumeAppendEntries(any(), eq(requestAppendEntriesRaftMessage.getAppendEntries()));
    }

    private void checkAppendEntriesResponseDispatch(MessageConsumer messageConsumer, Channel channel) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);

        doAnswer((Answer<Void>) invocation -> {
            latch.countDown();
            return null;
        }).when(messageConsumer).consumeAppendEntriesResponse(any(ActiveMember.class), any(AppendEntriesResponse.class));

        RaftMessage requestAppendEntriesResponseRaftMessage = getAppendEntriesResponseInstance();
        channel.writeAndFlush(requestAppendEntriesResponseRaftMessage);

        latch.await();

        verify(messageConsumer, times(1)).consumeAppendEntriesResponse(any(), eq(requestAppendEntriesResponseRaftMessage.getAppendEntriesResponse()));
    }

    @Test
    public void testProtocolMessageDispatcher() throws InterruptedException {
        Injector injector = Guice.createInjector(new Module(Configuration.newBuilder().selfId(new MemberId("localhost", 1234)).logUri(URI.create("file:///test")).build()));
        MemberConnector connector = injector.getInstance(MemberConnector.class);
        ChannelPipelineInitializer pipelineInitializer = injector.getInstance(ChannelPipelineInitializer.class);

        MessageConsumer messageConsumer = mock(MessageConsumer.class);
        MemberConnectorListenerTestableImpl listener = new MemberConnectorListenerTestableImpl(messageConsumer, pipelineInitializer);

        EchoServer server = new EchoServer(pipelineInitializer);
        server.start().awaitUninterruptibly();

        MemberId member = new MemberId("localhost", server.getPort());
        connector.register(member, listener);
        connector.connect(member);

        listener.awaitForConnectEvent();

        checkRequestVoteDispatch(messageConsumer, connector.getActiveMembers().get(member).getChannel());
        checkRequestVoteResponseDispatch(messageConsumer, connector.getActiveMembers().get(member).getChannel());
        checkAppendEntriesDispatch(messageConsumer, connector.getActiveMembers().get(member).getChannel());
        checkAppendEntriesResponseDispatch(messageConsumer, connector.getActiveMembers().get(member).getChannel());

        server.shutdown();
    }
}
